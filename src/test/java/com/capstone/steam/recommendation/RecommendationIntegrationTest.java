package com.capstone.steam.recommendation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** El chat de recomendaciones con el modelo simulado: nunca se llama a OpenRouter. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecommendationIntegrationTest {

    private static final String USER_MSG = "{\"messages\":[{\"role\":\"user\",\"content\":\"algo oscuro\"}]}";

    @Autowired private MockMvc mvc;
    @MockitoBean private ChatModel model;

    @Test
    void withoutASessionItIs401() throws Exception {
        mvc.perform(post("/api/recommendations/chat").contentType(MediaType.APPLICATION_JSON).content(USER_MSG))
                .andExpect(status().isUnauthorized());
        verify(model, never()).complete(anyString(), any());
    }

    @Test
    void withASessionItAnswersWithTheReplyAndTheCatalogGame() throws Exception {
        String token = register("chat_ok");
        long id = publishedGame(token, "Noche Eterna");
        when(model.complete(anyString(), any()))
                .thenReturn("{\"reply\":\"Prueba este\",\"gameIds\":[" + id + ",999999]}");

        chat(token, USER_MSG, "es")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Prueba este"))
                .andExpect(jsonPath("$.games.length()").value(1))
                .andExpect(jsonPath("$.games[0].title").value("Noche Eterna"));
    }

    @Test
    void badRequestsAre400AndDoNotReachTheModel() throws Exception {
        String token = register("chat_bad");
        String eleven = IntStream.range(0, 11).mapToObj(i -> "{\"role\":\"user\",\"content\":\"hola\"}")
                .collect(Collectors.joining(",", "{\"messages\":[", "]}"));
        String tooLong = "{\"messages\":[{\"role\":\"user\",\"content\":\"" + "a".repeat(501) + "\"}]}";
        String endsInAssistant = "{\"messages\":[{\"role\":\"user\",\"content\":\"hola\"},"
                + "{\"role\":\"assistant\",\"content\":\"dime\"}]}";

        chat(token, eleven, "es").andExpect(status().isBadRequest());
        chat(token, tooLong, "es").andExpect(status().isBadRequest());
        chat(token, "{\"messages\":[]}", "es").andExpect(status().isBadRequest());
        chat(token, endsInAssistant, "en").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The last message must be yours"));
        verify(model, never()).complete(anyString(), any());
    }

    @Test
    void aNullMessageInTheListIs400NotA500() throws Exception {
        String token = register("chat_null");

        chat(token, "{\"messages\":[null]}", "es").andExpect(status().isBadRequest());
        chat(token, "{\"messages\":[{\"role\":\"user\",\"content\":\"a\"},null,{\"role\":\"user\",\"content\":\"b\"}]}", "es")
                .andExpect(status().isBadRequest());
        verify(model, never()).complete(anyString(), any());
    }

    @Test
    void rejectedMessagesDoNotCountAgainstTheLimit() throws Exception {
        String token = register("chat_nocount");
        when(model.complete(anyString(), any())).thenReturn("{\"reply\":\"ok\",\"gameIds\":[]}");
        String endsInAssistant = "{\"messages\":[{\"role\":\"assistant\",\"content\":\"dime\"}]}";

        for (int i = 0; i < 25; i++) {
            chat(token, endsInAssistant, "es").andExpect(status().isBadRequest());
        }
        chat(token, USER_MSG, "es").andExpect(status().isOk());
    }

    @Test
    void anUnavailableModelIs503WithTheDetailInTheRequestLanguage() throws Exception {
        String token = register("chat_down");
        when(model.complete(anyString(), any())).thenThrow(new AssistantUnavailableException());

        chat(token, USER_MSG, "en")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("The assistant is not available right now"));
        chat(token, USER_MSG, "es")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("El asistente no está disponible ahora"));
    }

    @Test
    void theTwentyFirstRequestInAnHourIs429() throws Exception {
        String token = register("chat_limit");
        when(model.complete(anyString(), any())).thenReturn("{\"reply\":\"ok\",\"gameIds\":[]}");

        for (int i = 0; i < 20; i++) {
            chat(token, USER_MSG, "es").andExpect(status().isOk());
        }
        chat(token, USER_MSG, "en")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("You have sent too many messages to the assistant; try again later"));
    }

    // ------------------------------------------------------------------

    private ResultActions chat(String token, String body, String lang) throws Exception {
        return mvc.perform(post("/api/recommendations/chat")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT_LANGUAGE, lang)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long publishedGame(String token, String title) throws Exception {
        // el registro de DEVELOPER permite crear y publicar juegos
        Integer genreId = JsonPath.read(mvc.perform(get("/api/genres")).andReturn().getResponse().getContentAsString(),
                "$[0].id");
        String dev = registerDeveloper("dev_" + title.toLowerCase().replace(' ', '_'));
        String created = mvc.perform(post("/api/developer/games").header(HttpHeaders.AUTHORIZATION, "Bearer " + dev)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"%s","description":"Un juego oscuro","price":19.99,"genreIds":[%d]}""".formatted(title, genreId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();
        mvc.perform(post("/api/developer/games/" + id + "/publish").header(HttpHeaders.AUTHORIZATION, "Bearer " + dev))
                .andExpect(status().isOk());
        return id;
    }

    private String register(String username) throws Exception {
        return registerAs(username, "PLAYER");
    }

    private String registerDeveloper(String username) throws Exception {
        return registerAs(username, "DEVELOPER");
    }

    private String registerAs(String username, String type) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s@test.com","password":"password123","accountType":"%s"}"""
                        .formatted(username, username, type)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
