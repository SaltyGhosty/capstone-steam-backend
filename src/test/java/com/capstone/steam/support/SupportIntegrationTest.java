package com.capstone.steam.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * El formulario de asistencia con el correo simulado. No es @Transactional: el envío es asíncrono.
 * Usa su propio H2 para no mezclar cuentas con las de otros tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:support_it;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.mail.username=smtp-user@gmail.com", // activa el envío (el sender es un mock)
        "app.mail.from-name=ARCADE",
        "app.support.email=soporte@arcade-test.com"
})
class SupportIntegrationTest {

    private static final String MESSAGE = "No me carga la biblioteca desde ayer.";

    @Autowired private MockMvc mvc;
    @MockitoBean private JavaMailSender mailSender;

    /** Correos enviados: destinatario | asunto | Reply-To (o vacío). */
    private final List<String> sent = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        doAnswer(inv -> {
            MimeMessage m = inv.getArgument(0);
            String[] reply = m.getHeader("Reply-To");
            sent.add(m.getAllRecipients()[0] + "|" + m.getSubject() + "|" + (reply == null ? "" : reply[0]));
            return null;
        }).when(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void anAnonymousRequestWithAnEmailIs202AndSendsTwoMails() throws Exception {
        String email = unique("ana") + "@gmail.com";

        send(null, "{\"email\":\"%s\",\"subject\":\"Biblioteca vacía\",\"message\":\"%s\"}".formatted(email, MESSAGE), "en")
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        await().atMost(Duration.ofSeconds(5)).until(() -> sent.size() == 2);
        assertThat(sent).contains(
                "soporte@arcade-test.com|[Asistenza ARCADE] Biblioteca vacía|" + email,
                email + "|We have received your message|");
    }

    @Test
    void aLoggedInUserIgnoresTheEmailInTheBodyAndNeedsNone() throws Exception {
        String username = unique("bea");
        String token = register(username);

        send(token, "{\"email\":\"otro@gmail.com\",\"subject\":\"Hola soporte\",\"message\":\"%s\"}".formatted(MESSAGE), "es")
                .andExpect(status().isAccepted());
        send(token, "{\"subject\":\"Sin correo\",\"message\":\"%s\"}".formatted(MESSAGE), "es")
                .andExpect(status().isAccepted());

        // el registro también manda su correo de bienvenida: se cuentan solo los de asistencia
        await().atMost(Duration.ofSeconds(5)).until(() -> sent.stream()
                .filter(line -> line.contains("Asistenza") || line.contains("Hemos recibido")).count() == 4);
        String account = username + "@test.com";
        assertThat(sent).contains("soporte@arcade-test.com|[Asistenza ARCADE] Hola soporte|" + account,
                account + "|Hemos recibido tu mensaje|")
                .noneMatch(line -> line.contains("otro@gmail.com"));
    }

    @Test
    void invalidBodiesAre400AndSendNothing() throws Exception {
        String ok = "\"subject\":\"Un asunto\",\"message\":\"%s\"".formatted(MESSAGE);

        send(null, "{" + ok + "}", "es").andExpect(status().isBadRequest()); // sin sesión el email es obligatorio
        send(null, "{\"email\":\"no-es-un-email\"," + ok + "}", "es").andExpect(status().isBadRequest());
        send(null, "{\"email\":\"a@gmail.com\",\"subject\":\"ab\",\"message\":\"%s\"}".formatted(MESSAGE), "es")
                .andExpect(status().isBadRequest());
        send(null, "{\"email\":\"a@gmail.com\",\"subject\":\"%s\",\"message\":\"%s\"}".formatted("a".repeat(151), MESSAGE), "es")
                .andExpect(status().isBadRequest());
        send(null, "{\"email\":\"a@gmail.com\",\"subject\":\"Un asunto\",\"message\":\"corto\"}", "es")
                .andExpect(status().isBadRequest());
        send(null, "{\"email\":\"a@gmail.com\",\"subject\":\"Un asunto\",\"message\":\"%s\"}".formatted("a".repeat(3001)), "es")
                .andExpect(status().isBadRequest());
        // los espacios no cuentan: 10 espacios a cada lado de 3 letras no llegan a 10 caracteres
        send(null, "{\"email\":\"a@gmail.com\",\"subject\":\"Un asunto\",\"message\":\"          abc          \"}", "es")
                .andExpect(status().isBadRequest());

        verify(mailSender, after(500).never()).send(any(MimeMessage.class));
    }

    @Test
    void theSixthRequestFromTheSameEmailInAnHourIs429() throws Exception {
        String email = unique("rate") + "@gmail.com";
        String body = "{\"email\":\"%s\",\"subject\":\"Un asunto\",\"message\":\"%s\"}".formatted(email, MESSAGE);

        for (int i = 0; i < 5; i++) {
            send(null, body, "es").andExpect(status().isAccepted());
        }
        send(null, body, "en").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("You have sent too many support requests; try again in an hour"));
        send(null, body.replace(email, "otra_" + email), "es").andExpect(status().isAccepted()); // otro email no se ve afectado
        await().atMost(Duration.ofSeconds(5)).until(() -> sent.size() == 12); // que el envío asíncrono termine antes del siguiente test
    }

    // ------------------------------------------------------------------

    private ResultActions send(String token, String body, String lang) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/support").header(HttpHeaders.ACCEPT_LANGUAGE, lang)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(request);
    }

    private String register(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s@test.com","password":"password123"}""".formatted(username, username)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private static String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
