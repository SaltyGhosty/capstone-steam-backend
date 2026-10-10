package com.capstone.steam.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.capstone.steam.notification.event.UserRegisteredEvent;
import com.jayway.jsonpath.JsonPath;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * Correo asíncrono de verdad: esta clase NO es @Transactional, porque AFTER_COMMIT necesita
 * commits reales. Para no ensuciar la BD que comparten los demás tests, usa su propio H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mail_events;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.mail.username=smtp-user@gmail.com", // activa el envío (el sender es un mock)
        "app.mail.from-name=ARCADE" // el asunto de bienvenida lleva la marca; que no dependa del .env local
})
class MailEventIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationEventPublisher publisher;
    @Autowired private TransactionTemplate transactionTemplate;
    @MockitoBean private JavaMailSender mailSender;

    /** asunto -> nombre del hilo que lo envió */
    private final Map<String, String> sentBy = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        doAnswer(inv -> {
            MimeMessage message = inv.getArgument(0);
            sentBy.put(message.getSubject(), Thread.currentThread().getName());
            return null;
        }).when(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void registrationSendsWelcomeMailFromTheMailThreadPoolAfterCommit() throws Exception {
        register(unique("ana"), "PLAYER");

        await().atMost(Duration.ofSeconds(5)).until(() -> sentBy.containsKey("¡Bienvenido a ARCADE!"));
        assertThat(sentBy.get("¡Bienvenido a ARCADE!")).startsWith("mail-");
    }

    @Test
    void welcomeMailIsSentInTheLanguageChosenAtRegistration() throws Exception {
        String username = unique("liam");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s@test.com","password":"password123","locale":"en"}"""
                        .formatted(username, username)))
                .andExpect(status().isCreated());

        await().atMost(Duration.ofSeconds(5)).until(() -> sentBy.containsKey("Welcome to ARCADE!"));
        assertThat(sentBy.get("Welcome to ARCADE!")).startsWith("mail-");
    }

    @Test
    void rolledBackTransactionSendsNoMail() {
        transactionTemplate.executeWithoutResult(tx -> {
            publisher.publishEvent(new UserRegisteredEvent(999L, "ghost@test.com", "Ghost", "es"));
            tx.setRollbackOnly();
        });

        verify(mailSender, after(1000).never()).send(any(MimeMessage.class));
    }

    @Test
    void smtpFailureDoesNotBreakTheRequest() throws Exception {
        doThrow(new MailSendException("Gmail caído")).when(mailSender).send(any(MimeMessage.class));

        register(unique("bob"), "PLAYER"); // 201 aunque el correo falle

        verify(mailSender, timeout(5000)).send(any(MimeMessage.class));
    }

    @Test
    void purchaseSendsReceiptAndDeclinedPaymentSendsFailureNotice() throws Exception {
        String dev = register(unique("dev"), "DEVELOPER");
        String player = register(unique("eva"), "PLAYER");
        int gameId = publishGame(dev, unique("Game"));

        mvc.perform(auth(put("/api/cart/items/" + gameId), player)).andExpect(status().isOk());
        int declined = createOrder(player);
        mvc.perform(auth(post("/api/orders/" + declined + "/pay"), player).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CREDIT_CARD\",\"cardNumber\":\"4000000000000000\"}"))
                .andExpect(status().isPaymentRequired());
        int paid = createOrder(player);
        mvc.perform(auth(post("/api/orders/" + paid + "/pay"), player).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"PAYPAL\"}"))
                .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(5)).until(() ->
                sentBy.containsKey("No hemos podido cobrar tu pedido #" + declined)
                        && sentBy.containsKey("Recibo de tu compra #" + paid));
        assertThat(List.of(sentBy.get("Recibo de tu compra #" + paid))).allMatch(t -> t.startsWith("mail-"));
    }

    // ------------------------------------------------------------------

    private static String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private int createOrder(String token) throws Exception {
        return JsonPath.read(mvc.perform(auth(post("/api/orders"), token)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private int publishGame(String devToken, String title) throws Exception {
        List<Integer> genre = JsonPath.read(mvc.perform(get("/api/genres")).andReturn().getResponse()
                .getContentAsString(), "$[?(@.slug=='indie')].id");
        int id = JsonPath.read(mvc.perform(auth(post("/api/developer/games"), devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\",\"price\":9.99,\"genreIds\":[%d]}".formatted(title, genre.getFirst())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(auth(post("/api/developer/games/" + id + "/publish"), devToken)).andExpect(status().isOk());
        return id;
    }

    private String register(String username, String accountType) throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s@test.com","password":"password123","accountType":"%s"}"""
                        .formatted(username, username, accountType)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder auth(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
