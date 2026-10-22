package com.capstone.steam.friend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;
import com.jayway.jsonpath.JsonPath;

/** Chat entre amigos por HTTP: enviar, leer el historial y marcar como leído. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendMessageRepository messageRepository;

    private String ana;
    private String leo;
    private String sam;
    private String studio;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        ana = register("ana", "PLAYER");
        leo = register("leo", "PLAYER");
        sam = register("sam", "PLAYER");
        studio = register("studio", "DEVELOPER");
        adminToken = login("admin", "Admin12345!");
        befriend(ana, leo, "leo");
    }

    @Test
    void friendsCanWriteAndReadEachOther() throws Exception {
        mvc.perform(send(ana, "leo", "hola leo"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.body").value("hola leo"))
                .andExpect(jsonPath("$.createdAt").exists());

        mvc.perform(auth(get("/api/friends/ana/messages"), leo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mine").value(false))
                .andExpect(jsonPath("$[0].body").value("hola leo"));
        mvc.perform(auth(get("/api/friends/leo/messages"), ana))
                .andExpect(jsonPath("$[0].mine").value(true));
    }

    @Test
    void notFriendsGet403NotFriends() throws Exception {
        mvc.perform(send(sam, "leo", "hola"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("chat.notFriends"));
        // una solicitud pendiente tampoco basta
        mvc.perform(auth(post("/api/friends/requests"), sam).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"leo\"}")).andExpect(status().isCreated());
        mvc.perform(send(sam, "leo", "hola")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("chat.notFriends"));
    }

    @Test
    void exFriendsCanReadButNotWrite() throws Exception {
        mvc.perform(send(ana, "leo", "antes")).andExpect(status().isCreated());
        mvc.perform(auth(delete("/api/friends/leo"), ana)).andExpect(status().isNoContent());

        mvc.perform(auth(get("/api/friends/leo/messages"), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].body").value("antes"));
        mvc.perform(send(ana, "leo", "después")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("chat.notFriends"));
        mvc.perform(send(leo, "ana", "después")).andExpect(status().isForbidden());
    }

    @Test
    void writingAgainAfterBecomingFriendsAgain() throws Exception {
        mvc.perform(auth(delete("/api/friends/leo"), ana)).andExpect(status().isNoContent());
        mvc.perform(send(ana, "leo", "no")).andExpect(status().isForbidden());

        befriend(leo, ana, "ana");

        mvc.perform(send(ana, "leo", "otra vez")).andExpect(status().isCreated());
    }

    @Test
    void nobodyReadsAnotherConversation() throws Exception {
        mvc.perform(send(ana, "leo", "secreto")).andExpect(status().isCreated());

        // sam pide su propia conversación con leo y con ana: nunca ve la de ellos dos
        mvc.perform(auth(get("/api/friends/leo/messages"), sam))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(auth(get("/api/friends/ana/messages"), sam))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        // y marcar como leído no toca los mensajes de otros
        mvc.perform(auth(post("/api/friends/ana/messages/read"), sam)).andExpect(status().isNoContent());
        assertThat(messageRepository.findAll()).singleElement().satisfies(m -> assertThat(m.getReadAt()).isNull());
        // sin sesión no hay nada
        mvc.perform(get("/api/friends/leo/messages")).andExpect(status().isUnauthorized());
    }

    @Test
    void blankOrTooLongBodyIs400() throws Exception {
        mvc.perform(send(ana, "leo", "")).andExpect(status().isBadRequest());
        mvc.perform(send(ana, "leo", "   \\n  ")).andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/friends/leo/messages"), ana).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(send(ana, "leo", "x".repeat(1001))).andExpect(status().isBadRequest());

        // 1.000 justos valen, también con espacios en los extremos (no cuentan)
        mvc.perform(send(ana, "leo", "  " + "x".repeat(1000) + "  ")).andExpect(status().isCreated());
        // otros espacios Unicode en los extremos tampoco cuentan
        mvc.perform(send(ana, "leo", " tab ")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("tab"));
        // el salto de línea del medio se guarda tal cual; los extremos se recortan
        mvc.perform(send(ana, "leo", "  una\\ndos  ")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("una\ndos"));
        mvc.perform(auth(get("/api/friends/leo/messages").param("size", "1"), ana))
                .andExpect(jsonPath("$[0].body").value("una\ndos"));
    }

    @Test
    void markReadOnlyTouchesReceivedMessages() throws Exception {
        mvc.perform(send(ana, "leo", "de ana")).andExpect(status().isCreated());
        mvc.perform(send(leo, "ana", "de leo")).andExpect(status().isCreated());
        mvc.perform(send(sam, "ana", "de sam")).andExpect(status().isForbidden());

        mvc.perform(auth(post("/api/friends/ana/messages/read"), leo)).andExpect(status().isNoContent());
        // idempotente
        mvc.perform(auth(post("/api/friends/ana/messages/read"), leo)).andExpect(status().isNoContent());

        assertThat(messageRepository.findAll()).hasSize(2).allSatisfy(m -> {
            if (m.getBody().equals("de ana")) {
                assertThat(m.getReadAt()).isNotNull(); // leo lo recibió y lo leyó
            } else {
                assertThat(m.getReadAt()).isNull(); // el suyo lo tiene que leer ana
            }
        });
    }

    @Test
    void cursorPaginationReturnsOlderMessages() throws Exception {
        User anaUser = userRepository.findByUsernameIgnoreCase("ana").orElseThrow();
        User leoUser = userRepository.findByUsernameIgnoreCase("leo").orElseThrow();
        for (int i = 1; i <= 35; i++) {
            messageRepository.save(i % 2 == 0 ? new FriendMessage(anaUser, leoUser, "m" + i)
                    : new FriendMessage(leoUser, anaUser, "m" + i));
        }
        messageRepository.flush();

        String first = mvc.perform(auth(get("/api/friends/leo/messages"), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(30))
                .andExpect(jsonPath("$[0].body").value("m35"))
                .andExpect(jsonPath("$[29].body").value("m6"))
                .andReturn().getResponse().getContentAsString();
        int oldestId = JsonPath.read(first, "$[29].id");

        mvc.perform(auth(get("/api/friends/leo/messages").param("before", String.valueOf(oldestId)), ana))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].body").value("m5"))
                .andExpect(jsonPath("$[4].body").value("m1"));
        // el tamaño se acota a 100 y a 1 como mínimo
        mvc.perform(auth(get("/api/friends/leo/messages").param("size", "1000"), ana))
                .andExpect(jsonPath("$.length()").value(35));
        mvc.perform(auth(get("/api/friends/leo/messages").param("size", "0"), ana))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void pausedRecipientIs404AndDeveloperSenderIs403PlayersOnly() throws Exception {
        mvc.perform(send(ana, "leo", "antes")).andExpect(status().isCreated());
        long leoId = userRepository.findByUsernameIgnoreCase("leo").orElseThrow().getId();
        mvc.perform(auth(patch("/api/admin/users/" + leoId + "/enabled"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mvc.perform(send(ana, "leo", "hola")).andExpect(status().isNotFound());
        // leer sí se puede: el historial con una cuenta en pausa sigue ahí
        mvc.perform(auth(get("/api/friends/leo/messages"), ana)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].body").value("antes"));
        mvc.perform(auth(post("/api/friends/leo/messages/read"), ana)).andExpect(status().isNoContent());
        // una cuenta que no existe no tiene historial
        mvc.perform(auth(get("/api/friends/nadie/messages"), ana)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/friends/nadie/messages/read"), ana)).andExpect(status().isNotFound());
        // un estudio existe pero no tiene historial
        mvc.perform(auth(get("/api/friends/studio/messages"), ana)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(auth(post("/api/friends/studio/messages/read"), ana)).andExpect(status().isNoContent());
        mvc.perform(send(ana, "nadie", "hola")).andExpect(status().isNotFound());
        // un estudio no puede escribir (403 playersOnly) ni siquiera a alguien que no existe
        mvc.perform(send(studio, "ana", "hola")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("friend.playersOnly"));
        mvc.perform(send(studio, "nadie", "hola")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("friend.playersOnly"));
        // y un estudio como destinatario es invisible
        mvc.perform(send(ana, "studio", "hola")).andExpect(status().isNotFound());
    }

    @Test
    void messagingYourselfIs400BeforeAnythingElse() throws Exception {
        mvc.perform(send(ana, "ANA", "hola")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("friend.self"));
        mvc.perform(send(studio, "studio", "hola")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("friend.self"));
        mvc.perform(send(ana, "ana", "")).andExpect(status().isBadRequest()); // la validación va antes
    }

    @Test
    void thirtyOneMessagesInAMinuteGive429() throws Exception {
        for (int i = 1; i <= 30; i++) {
            mvc.perform(send(ana, "leo", "m" + i)).andExpect(status().isCreated());
        }
        mvc.perform(send(ana, "leo", "m31"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("chat.rateLimited"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        // el límite es por usuario: leo sigue pudiendo escribir
        mvc.perform(send(leo, "ana", "yo sí")).andExpect(status().isCreated());
    }

    // ------------------------------ ayudas ------------------------------

    /** Cuerpo JSON escrito a mano: {@code text} ya lleva las secuencias de escape de JSON (por ejemplo \\n). */
    private MockHttpServletRequestBuilder send(String token, String username, String text) {
        return auth(post("/api/friends/" + username + "/messages"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"%s\"}".formatted(text));
    }

    private void befriend(String from, String to, String toUsername) throws Exception {
        String body = mvc.perform(auth(post("/api/friends/requests"), from).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\"}".formatted(toUsername)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.id");
        mvc.perform(auth(post("/api/friends/requests/" + id + "/accept"), to)).andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String register(String username, String accountType) throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s@test.com","password":"password123","accountType":"%s"}"""
                        .formatted(username, username, accountType)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private String login(String login, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, password)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }
}
