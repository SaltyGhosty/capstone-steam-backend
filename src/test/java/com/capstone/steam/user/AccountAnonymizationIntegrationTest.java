package com.capstone.steam.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.capstone.steam.notification.event.AccountDeletedEvent;
import com.capstone.steam.support.MutableClock;
import com.jayway.jsonpath.JsonPath;

/**
 * Anonimización al acabar el plazo. {@code AccountAnonymizer} abre su propia transacción (REQUIRES_NEW) y
 * confirma, así que un {@code @Transactional} de clase no la desharía ni vería sus cambios: este test es como los
 * de concurrencia, con commits reales, su propia H2 y las tablas vaciadas (salvo el admin) antes de cada test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableClock.Config.class)
@RecordApplicationEvents
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:account_anonymization;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.mail.username=" // el .env local puede traer un Gmail: este test no envía correos
})
class AccountAnonymizationIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");
    private static final String GOOD_CARD = "{\"method\":\"CREDIT_CARD\",\"cardNumber\":\"4242424242424242\"}";

    @Autowired private MockMvc mvc;
    @Autowired private MutableClock clock;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AccountDeletionScheduler scheduler;
    @Autowired private ApplicationEvents events;
    @MockitoSpyBean private AccountAnonymizer anonymizer;

    @BeforeEach
    void cleanDatabase() {
        for (String table : List.of("payments", "library_entries", "order_items", "orders", "reviews", "cart_items",
                "wishlist_items", "friend_messages", "friendships", "refresh_tokens", "email_verification_tokens", "game_genres",
                "games")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username <> 'admin')");
        jdbc.update("DELETE FROM users WHERE username <> 'admin'");
        clock.now = T0;
    }

    @Test
    void notAnonymizedBeforeTheDate() throws Exception {
        String token = register("pronto", "pronto@test.com", "PLAYER");
        requestDeletion(token);

        clock.now = T0.plus(java.time.Duration.ofDays(14)).minusSeconds(60);

        assertThat(scheduler.runDue()).isZero();
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE email = 'pronto@test.com'", String.class))
                .isEqualTo("pronto");
    }

    @Test
    void neverBeforeTheDateEvenWhenCalledDirectly() throws Exception {
        String token = register("temprano", "temprano@test.com", "PLAYER");
        requestDeletion(token);
        long id = userId("temprano");
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM users WHERE id = ?", id);

        clock.now = T0.plus(java.time.Duration.ofDays(14)).minusSeconds(1);

        assertThat(anonymizer.anonymize(id)).isFalse();
        assertThat(jdbc.queryForMap("SELECT * FROM users WHERE id = ?", id)).isEqualTo(before);
        assertThat(events.stream(AccountDeletedEvent.class)).isEmpty();
    }

    @Test
    void runDueCountsOnlyTheAccountsReallyAnonymized() throws Exception {
        requestDeletion(register("arrepentido", "arrepentido@test.com", "PLAYER"));
        long id = userId("arrepentido");
        clock.now = T0.plus(java.time.Duration.ofDays(14));
        // la tarea ya la tiene en su lista, pero se recupera justo antes de que el anonimizador la bloquee
        doAnswer(invocation -> {
            jdbc.update("UPDATE users SET deletion_scheduled_at = NULL, deletion_requested_by = NULL WHERE id = ?", id);
            return invocation.callRealMethod();
        }).when(anonymizer).anonymize(eq(id));

        assertThat(scheduler.runDue()).isZero();
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, id))
                .isEqualTo("arrepentido");
    }

    @Test
    void anonymizesAfterTheDate() throws Exception {
        String studio = register("estudio", "estudio@test.com", "DEVELOPER");
        String victim = register("victima", "victima@test.com", "PLAYER");
        String friend = register("amigo", "amigo@test.com", "PLAYER");
        String admirer = register("admirador", "admirador@test.com", "PLAYER");
        int hades = publishGame(studio, "Hades");
        int celeste = publishGame(studio, "Celeste");
        buy(victim, hades);
        mvc.perform(auth(post("/api/reviews"), victim).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":%d,\"recommended\":true,\"body\":\"genial\",\"language\":\"es\"}".formatted(hades)))
                .andExpect(status().isCreated());
        mvc.perform(auth(put("/api/cart/items/" + celeste), victim)).andExpect(status().isOk());
        mvc.perform(auth(put("/api/wishlist/" + celeste), victim)).andExpect(status().isNoContent());
        mvc.perform(auth(post("/api/friends/requests"), victim).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"amigo\"}")).andExpect(status().isCreated());
        // y una solicitud entrante: aquí el eliminado es el destinatario
        mvc.perform(auth(post("/api/friends/requests"), admirer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"victima\"}")).andExpect(status().isCreated());
        long victimId = userId("victima");
        long admirerId = userId("admirador");
        jdbc.update("""
                INSERT INTO email_verification_tokens (user_id, token_hash, expires_at, username, password_hash)
                VALUES (?, 'hash-victima', CURRENT_TIMESTAMP, 'victima', 'x')""", victimId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM friendships WHERE addressee_id = ?", Integer.class,
                victimId)).isOne();
        assertThat(jdbc.queryForObject("SELECT review_count FROM games WHERE id = ?", Integer.class, hades)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, victimId))
                .isPositive();
        requestDeletion(victim);
        // un amigo cualquiera sigue existiendo: solo se va lo del usuario eliminado
        assertThat(friend).isNotBlank();

        clock.now = T0.plus(java.time.Duration.ofDays(14));
        assertThat(scheduler.runDue()).isOne();

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM users WHERE id = ?", victimId);
        assertThat(row.get("username")).isEqualTo("deleted_" + victimId);
        assertThat(row.get("email")).isEqualTo("deleted_" + victimId + "@deleted.invalid");
        assertThat(row.get("display_name")).isNull();
        assertThat(row.get("enabled")).isEqualTo(false);
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("deletion_scheduled_at")).isNull();
        String hash = (String) row.get("password_hash");
        assertThat(passwordEncoder.matches("password123", hash)).isFalse();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"victima\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(count("reviews", victimId)).isZero();
        assertThat(jdbc.queryForMap("SELECT review_count, positive_count FROM games WHERE id = ?", hades))
                .containsEntry("review_count", 0).containsEntry("positive_count", 0);
        assertThat(count("cart_items", victimId)).isZero();
        assertThat(count("wishlist_items", victimId)).isZero();
        assertThat(count("refresh_tokens", victimId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM friendships WHERE requester_id = ? OR addressee_id = ?", Integer.class,
                victimId, victimId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM friendships WHERE requester_id = ?", Integer.class,
                admirerId)).isZero();
        assertThat(count("email_verification_tokens", victimId)).isZero();
        // pedidos y biblioteca se conservan
        assertThat(count("orders", victimId)).isOne();
        assertThat(count("library_entries", victimId)).isOne();
        assertThat(events.stream(AccountDeletedEvent.class)).singleElement()
                .satisfies(e -> {
                    assertThat(e.email()).isEqualTo("victima@test.com");
                    assertThat(e.locale()).isNotNull();
                });
    }

    @Test
    void anonymizationDeletesTheConversations() throws Exception {
        String victim = register("charlador", "charlador@test.com", "PLAYER");
        String friend = register("compi", "compi@test.com", "PLAYER");
        String other = register("tercero", "tercero@test.com", "PLAYER");
        befriend(victim, friend, "compi");
        befriend(other, friend, "compi");
        sendMessage(victim, "compi", "enviado");
        sendMessage(friend, "charlador", "recibido");
        sendMessage(other, "compi", "ajeno");
        long victimId = userId("charlador");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM friend_messages", Integer.class)).isEqualTo(3);
        requestDeletion(victim);

        clock.now = T0.plus(java.time.Duration.ofDays(14));
        assertThat(scheduler.runDue()).isOne();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM friend_messages WHERE sender_id = ? OR recipient_id = ?", Integer.class,
                victimId, victimId)).isZero();
        // la conversación de los otros dos no se toca
        assertThat(jdbc.queryForList("SELECT body FROM friend_messages", String.class)).containsExactly("ajeno");
    }

    private void befriend(String from, String to, String toUsername) throws Exception {
        String body = mvc.perform(auth(post("/api/friends/requests"), from).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\"}".formatted(toUsername)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.id");
        mvc.perform(auth(post("/api/friends/requests/" + id + "/accept"), to)).andExpect(status().isOk());
    }

    private void sendMessage(String token, String username, String text) throws Exception {
        mvc.perform(auth(post("/api/friends/" + username + "/messages"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"%s\"}".formatted(text)))
                .andExpect(status().isCreated());
    }

    @Test
    void studioGamesDelistedDraftsDeletedBuyersKeepThem() throws Exception {
        String studio = register("estudio2", "estudio2@test.com", "DEVELOPER");
        String buyer = register("comprador", "comprador@test.com", "PLAYER");
        int published = publishGame(studio, "Publicado");
        int draft = createDraft(studio, "Borrador");
        int draftWithWishlist = createDraft(studio, "Borrador con deseo");
        buy(buyer, published);
        jdbc.update("INSERT INTO wishlist_items (user_id, game_id) VALUES (?, ?)", userId("comprador"),
                draftWithWishlist);
        long studioId = userId("estudio2");
        requestDeletion(studio);

        clock.now = T0.plus(java.time.Duration.ofDays(14));
        assertThat(scheduler.runDue()).isOne();

        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, studioId))
                .isEqualTo("deleted_" + studioId);
        assertThat(statusOf(published)).isEqualTo("DELISTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM games WHERE id = ?", Integer.class, draft)).isZero();
        assertThat(statusOf(draftWithWishlist)).isEqualTo("DELISTED");
        mvc.perform(auth(get("/api/library"), buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Publicado"));
    }

    @Test
    void freedEmailAndUsernameCanRegisterAgain() throws Exception {
        String token = register("libre", "libre@test.com", "PLAYER");
        requestDeletion(token);
        long oldId = userId("libre");
        clock.now = T0.plus(java.time.Duration.ofDays(14));
        assertThat(scheduler.runDue()).isOne();

        register("libre", "libre@test.com", "PLAYER");

        long newId = userId("libre");
        assertThat(newId).isNotEqualTo(oldId);
        // la fila anonimizada sigue intacta
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, oldId))
                .isEqualTo("deleted_" + oldId);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM users WHERE id = ?", Boolean.class, oldId))
                .isTrue();
    }

    @Test
    void cartWithDelistedGameFailsCheckoutCleanly() throws Exception {
        String studio = register("estudio3", "estudio3@test.com", "DEVELOPER");
        String buyer = register("carrito", "carrito@test.com", "PLAYER");
        int game = publishGame(studio, "Retirado");
        mvc.perform(auth(put("/api/cart/items/" + game), buyer)).andExpect(status().isOk());
        requestDeletion(studio);
        clock.now = T0.plus(java.time.Duration.ofDays(14));
        assertThat(scheduler.runDue()).isOne();

        mvc.perform(auth(post("/api/orders"), buyer))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void oneFailingAccountDoesNotStopTheOthers() throws Exception {
        requestDeletion(register("falla", "falla@test.com", "PLAYER"));
        requestDeletion(register("sigue", "sigue@test.com", "PLAYER"));
        long failingId = userId("falla");
        long okId = userId("sigue");
        doThrow(new IllegalStateException("fallo simulado")).when(anonymizer).anonymize(eq(failingId));

        clock.now = T0.plus(java.time.Duration.ofDays(14));

        assertThat(scheduler.runDue()).isOne();
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, failingId))
                .isEqualTo("falla");
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, okId))
                .isEqualTo("deleted_" + okId);
    }

    @Test
    void anAccountRecoveredBeforeTheRunIsLeftAlone() throws Exception {
        String token = register("vuelve", "vuelve@test.com", "PLAYER");
        requestDeletion(token);
        long id = userId("vuelve");
        clock.now = T0.plus(java.time.Duration.ofDays(14));
        // la tarea ya la tenía en su lista, pero se recupera justo antes de bloquearla
        jdbc.update("UPDATE users SET deletion_scheduled_at = NULL, deletion_requested_by = NULL WHERE id = ?", id);

        anonymizer.anonymize(id);

        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, id))
                .isEqualTo("vuelve");
        assertThat(events.stream(AccountDeletedEvent.class)).isEmpty();
    }

    @Test
    void adminListHidesDeletedAccounts() throws Exception {
        String token = register("oculta", "oculta@test.com", "PLAYER");
        requestDeletion(token);
        long id = userId("oculta");
        String admin = login("admin", "Admin12345!");
        mvc.perform(auth(get("/api/admin/users"), admin).param("size", "100"))
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")].username").value("oculta"));

        clock.now = T0.plus(java.time.Duration.ofDays(14));
        assertThat(scheduler.runDue()).isOne();

        mvc.perform(auth(get("/api/admin/users"), admin).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")]").isEmpty());
    }

    // ------------------------------------------------------------------ ayudas

    private long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private int count(String table, long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }

    private String statusOf(int gameId) {
        return jdbc.queryForObject("SELECT status FROM games WHERE id = ?", String.class, gameId);
    }

    private void requestDeletion(String token) throws Exception {
        mvc.perform(auth(post("/api/users/me/deletion"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String register(String username, String email, String accountType) throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s","password":"password123","accountType":"%s"}"""
                        .formatted(username, email, accountType)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private String login(String login, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private int createDraft(String devToken, String title) throws Exception {
        List<Integer> genre = JsonPath.read(mvc.perform(get("/api/genres")).andReturn().getResponse()
                .getContentAsString(), "$[?(@.slug=='indie')].id");
        String created = mvc.perform(auth(post("/api/developer/games"), devToken).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","price":%s,"discountPercent":0,"genreIds":[%d]}"""
                                .formatted(title, "10.00", genre.getFirst())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    private int publishGame(String devToken, String title) throws Exception {
        int id = createDraft(devToken, title);
        mvc.perform(auth(post("/api/developer/games/" + id + "/publish"), devToken)).andExpect(status().isOk());
        return id;
    }

    private void buy(String playerToken, int gameId) throws Exception {
        mvc.perform(auth(put("/api/cart/items/" + gameId), playerToken)).andExpect(status().isOk());
        ResultActions order = mvc.perform(auth(post("/api/orders"), playerToken)).andExpect(status().isCreated());
        int orderId = JsonPath.read(order.andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(auth(post("/api/orders/" + orderId + "/pay"), playerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(GOOD_CARD))
                .andExpect(status().isOk());
    }
}
