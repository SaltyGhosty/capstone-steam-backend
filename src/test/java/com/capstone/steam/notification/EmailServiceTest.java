package com.capstone.steam.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.capstone.steam.notification.event.OrderPaidEvent;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

class EmailServiceTest {

    private static final String STORE = "https://arcade.test";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    /** Mismo nombre que la excepción de la implementación de Jakarta Mail (que no está en el classpath de compilación). */
    private static class MailConnectException extends jakarta.mail.MessagingException {
        MailConnectException(String message) {
            super(message);
        }
    }

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
    }

    @Test
    void neverSendsToReservedDomainsLikeTheDemoAccounts() {
        var service = new EmailService(mailSender, "tienda@capstone.test", "ARCADE", STORE, "smtp-user@gmail.com");

        service.sendWelcome("demo_player@demo.capstone-steam.local", "Demo", "es", null, null);
        service.sendWelcome("ana@example.com", "Ana", "es", null, null);
        service.sendWelcome("bob@shop.test", "Bob", "es", null, null);

        verifyNoInteractions(mailSender);
        assertThat(EmailService.isReservedDomain("player@test.com")).isFalse(); // test.com es un dominio real
        assertThat(EmailService.isReservedDomain("sin-arroba")).isTrue();
    }

    @Test
    void supportRequestGoesToTheSupportAddressWithReplyToTheUser() throws Exception {
        var service = new EmailService(mailSender, "tienda@capstone.test", "ARCADE", STORE, "smtp-user@gmail.com");

        service.sendSupportRequest("soporte@arcade-shop.com", "ana@gmail.com", "Ana", "ana", "No carga <b>mi</b> biblioteca",
                "Hola,\nno me carga.", "it");

        MimeMessage message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("[Assistenza ARCADE] No carga <b>mi</b> biblioteca");
        assertThat(((InternetAddress) message.getAllRecipients()[0]).getAddress()).isEqualTo("soporte@arcade-shop.com");
        assertThat(((InternetAddress) message.getReplyTo()[0]).getAddress()).isEqualTo("ana@gmail.com");
        String html = (String) message.getContent();
        assertThat(html).contains("ana@gmail.com", "@ana", "white-space:pre-wrap", "Hola,\nno me carga.")
                .doesNotContain("<b>mi</b>");
    }

    @Test
    void supportSubjectCannotInjectHeaders() throws Exception {
        var service = new EmailService(mailSender, "tienda@capstone.test", "ARCADE", STORE, "smtp-user@gmail.com");

        service.sendSupportRequest("soporte@arcade-shop.com", "ana@gmail.com", null, null, "Hola\r\nBcc: x@y.com",
                "mensaje de prueba", "es");

        assertThat(sentMessage().getSubject()).isEqualTo("[Assistenza ARCADE] Hola Bcc: x@y.com");
    }

    @Test
    void supportConfirmationIsInTheUsersLanguageAndHasNoReplyTo() throws Exception {
        var service = new EmailService(mailSender, "tienda@capstone.test", "ARCADE", STORE, "smtp-user@gmail.com");

        service.sendSupportConfirmation("ana@gmail.com", "Ana", "Mi asunto", "en");

        MimeMessage message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("We have received your message");
        assertThat(message.getReplyTo()[0]).isEqualTo(message.getFrom()[0]); // sin Reply-To propio, JavaMail devuelve el remitente
        assertThat((String) message.getContent()).contains("Mi asunto");
    }

    @Test
    void sendsHtmlReceiptWithEscapedUserData() throws Exception {
        var service = new EmailService(mailSender, "tienda@capstone.test", "ARCADE", STORE, "smtp-user@gmail.com");

        service.sendPurchaseReceipt("player@test.com", "<b>Hacker</b>", 42L, new BigDecimal("39.99"),
                List.of(line("Celeste", null, "15.00"), line("Hades <3", null, "24.99")), "es");

        MimeMessage message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("Recibo de tu compra #42");
        assertThat(((InternetAddress) message.getAllRecipients()[0]).getAddress()).isEqualTo("player@test.com");
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("tienda@capstone.test");

        String html = (String) message.getContent();
        assertThat(html).contains("&lt;b&gt;Hacker&lt;/b&gt;").doesNotContain("<b>Hacker</b>");
        assertThat(html).contains("Celeste", "Hades &lt;3", "39,99", "Pedido #42");
        assertThat(html).contains("Estos juegos ya están en tu biblioteca");
    }

    @Test
    void receiptShowsThePricePaidForEachGame() throws Exception {
        receipt(List.of(line("Celeste", null, "15.00"), line("Hades", null, "24.99")), "39.99");

        String html = (String) sentMessage().getContent();
        assertThat(html).contains(EmailTemplates.money(new BigDecimal("15.00"), MailTexts.ES),
                EmailTemplates.money(new BigDecimal("24.99"), MailTexts.ES), EmailTemplates.money(new BigDecimal("39.99"), MailTexts.ES));
    }

    @Test
    void receiptShowsTheCoverWhenItIsAnHttpsUrl() throws Exception {
        receipt(List.of(line("Celeste", "https://img.test/celeste.jpg", "15.00")), "15.00");

        String html = (String) sentMessage().getContent();
        assertThat(html).contains("<img src=\"https://img.test/celeste.jpg\"", "width=\"64\"", "height=\"36\"",
                "alt=\"Celeste\"");
    }

    @Test
    void uppercaseHttpsSchemeIsAccepted() throws Exception {
        receipt(List.of(line("Celeste", "HTTPS://img.test/c.jpg", "15.00")), "15.00");

        assertThat((String) sentMessage().getContent()).contains("<img src=\"HTTPS://img.test/c.jpg\"");
    }

    @Test
    void receiptDropsPlainHttpCover() throws Exception {
        receipt(List.of(line("Celeste", "http://img.test/c.jpg", "15.00")), "15.00");

        assertThat((String) sentMessage().getContent()).doesNotContain("<img");
    }

    @Test
    void receiptWithoutCoverOrWithUnsafeUrlHasNoImage() throws Exception {
        receipt(List.of(line("Sin portada", null, "5.00"), line("Vacía", "  ", "5.00"),
                line("Malicioso", "javascript:alert(1)", "5.00"), line("Data", "data:image/png;base64,AAAA", "5.00")),
                "20.00");

        String html = (String) sentMessage().getContent();
        assertThat(html).doesNotContain("<img", "javascript:", "data:image");
        assertThat(html).contains("Sin portada", "Malicioso", EmailTemplates.money(new BigDecimal("5.00"), MailTexts.ES));
    }

    @Test
    void titleAndCoverUrlAreEscaped() throws Exception {
        receipt(List.of(line("<script>x</script>", "https://img.test/a.jpg?a=1&b=\"><script>", "9.99")), "9.99");

        String html = (String) sentMessage().getContent();
        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;x&lt;/script&gt;",
                "src=\"https://img.test/a.jpg?a=1&amp;b=&quot;&gt;&lt;script&gt;\"",
                "alt=\"&lt;script&gt;x&lt;/script&gt;\"");
    }

    @Test
    void safeImageUrlOnlyAcceptsHttps() {
        assertThat(EmailTemplates.safeImageUrl("https://a.test/x.png")).isEqualTo("https://a.test/x.png");
        assertThat(EmailTemplates.safeImageUrl("http://a.test/x.png")).isNull();
        assertThat(EmailTemplates.safeImageUrl(" HtTpS://a.test/x.png ")).isEqualTo("HtTpS://a.test/x.png");
        assertThat(EmailTemplates.safeImageUrl("javascript:alert(1)")).isNull();
        assertThat(EmailTemplates.safeImageUrl("//a.test/x.png")).isNull();
        assertThat(EmailTemplates.safeImageUrl("/covers/x.png")).isNull();
        assertThat(EmailTemplates.safeImageUrl("")).isNull();
        assertThat(EmailTemplates.safeImageUrl(null)).isNull();
    }

    @Test
    void eachPriceSitsInTheRowOfItsGame() throws Exception {
        receipt(List.of(line("Celeste", null, "15.00"), line("Hades", null, "24.99")), "39.99");

        String html = (String) sentMessage().getContent();
        String celesteRow = rowOf(html, "Celeste");
        String hadesRow = rowOf(html, "Hades");
        assertThat(celesteRow).contains(EmailTemplates.money(new BigDecimal("15.00"), MailTexts.ES))
                .doesNotContain(EmailTemplates.money(new BigDecimal("24.99"), MailTexts.ES));
        assertThat(hadesRow).contains(EmailTemplates.money(new BigDecimal("24.99"), MailTexts.ES))
                .doesNotContain(EmailTemplates.money(new BigDecimal("15.00"), MailTexts.ES));
    }

    @Test
    void lineWithoutPriceStillRendersWithAnEmptyPriceCell() throws Exception {
        receipt(List.of(new OrderPaidEvent.Line("Celeste", null, null)), "0.00");

        String html = (String) sentMessage().getContent();
        assertThat(rowOf(html, "Celeste")).doesNotContain("null", "€");
    }

    @Test
    void rowWithoutCoverKeepsTheFixedWidthThumbnailCell() throws Exception {
        receipt(List.of(line("Celeste", "javascript:alert(1)", "15.00")), "15.00");

        assertThat(rowOf((String) sentMessage().getContent(), "Celeste")).contains("width=\"76\"", "&nbsp;");
    }

    @Test
    void safeImageUrlRejectsDisguisedOrNonHttpSchemes() {
        assertThat(EmailTemplates.safeImageUrl("  JavaScript:alert(1)")).isNull();
        assertThat(EmailTemplates.safeImageUrl("DATA:image/png;base64,AAAA")).isNull();
        assertThat(EmailTemplates.safeImageUrl("data:image/png;base64,AAAA")).isNull();
        assertThat(EmailTemplates.safeImageUrl("vbscript:msgbox(1)")).isNull();
        assertThat(EmailTemplates.safeImageUrl("ftp://a.test/x.png")).isNull();
        assertThat(EmailTemplates.safeImageUrl("file:///C:/x.png")).isNull();
        assertThat(EmailTemplates.safeImageUrl("https//a.test/x.png")).isNull();
        assertThat(EmailTemplates.safeImageUrl("httpsx://a.test/x.png")).isNull();
        assertThat(EmailTemplates.safeImageUrl("   ")).isNull();
        assertThat(EmailTemplates.safeImageUrl("http://a.test/x.png")).isNull();
    }

    @Test
    void coverUrlCannotBreakOutOfTheSrcAttribute() throws Exception {
        receipt(List.of(line("Celeste", "https://img.test/x.jpg\" onerror=\"alert(1)", "15.00")), "15.00");

        String html = (String) sentMessage().getContent();
        assertThat(html).doesNotContain("\" onerror=\"")
                .contains("src=\"https://img.test/x.jpg&quot; onerror=&quot;alert(1)\"");
    }

    /** La fila {@code <tr>...</tr>} del recibo que contiene el título dado. */
    private static String rowOf(String html, String title) {
        for (String chunk : html.split("<tr>")) {
            int end = chunk.indexOf("</tr>");
            String row = end < 0 ? chunk : chunk.substring(0, end);
            if (row.contains(">" + title + "<")) {
                return row;
            }
        }
        throw new AssertionError("No hay fila para " + title);
    }

    private void receipt(List<OrderPaidEvent.Line> lines, String total) {
        new EmailService(mailSender, "tienda@capstone.test", "ARCADE", STORE, "smtp-user@gmail.com")
                .sendPurchaseReceipt("a@test.com", "Ana", 1L, new BigDecimal(total), lines, "es");
    }

    private static OrderPaidEvent.Line line(String title, String coverUrl, String price) {
        return new OrderPaidEvent.Line(title, coverUrl, new BigDecimal(price));
    }

    @Test
    void mailArrivesWithTheStoreNameAsSender() throws Exception {
        new EmailService(mailSender, "tienda@capstone.test", "Mi Tienda", STORE, "smtp-user@gmail.com")
                .sendWelcome("a@test.com", "Ana", "es", null, null);

        MimeMessage message = sentMessage();
        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getPersonal()).isEqualTo("Mi Tienda");
        assertThat(from.getAddress()).isEqualTo("tienda@capstone.test");
        assertThat(message.getSubject()).isEqualTo("¡Bienvenido a Mi Tienda!");
        assertThat((String) message.getContent()).contains("¡Hola, Ana!", "Mi Tienda");
    }

    @Test
    void emptySenderNameFallsBackToArcade() throws Exception {
        new EmailService(mailSender, "tienda@capstone.test", " ", STORE, "smtp-user@gmail.com")
                .sendWelcome("a@test.com", "Ana", "es", null, null);

        MimeMessage message = sentMessage();
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("ARCADE");
        assertThat(message.getSubject()).isEqualTo("¡Bienvenido a ARCADE!");
    }

    @Test
    void receiptHasNoButtonBackToTheStore() throws Exception {
        var service = new EmailService(mailSender, "", "ARCADE", "https://arcade.test/, http://localhost:5173",
                "smtp-user@gmail.com");

        service.sendPurchaseReceipt("a@test.com", "Ana", 7L, BigDecimal.TEN, List.of(line("Celeste", null, "10.00")),
                "es");

        String html = (String) sentMessage().getContent();
        assertThat(html).doesNotContain("href=\"https://arcade.test/library\"");
        assertThat(html).doesNotContain("localhost");
    }

    @Test
    void withoutStoreUrlTheMailHasNoLinks() throws Exception {
        new EmailService(mailSender, "", "ARCADE", "", "smtp-user@gmail.com")
                .sendPaymentFailed("a@test.com", "Ana", 1L, BigDecimal.TEN, "Tarjeta rechazada", "es");

        String html = (String) sentMessage().getContent();
        assertThat(html).contains("Tarjeta rechazada", "Hola, Ana.").doesNotContain("href=");
    }

    @Test
    void userWithoutDisplayNameGetsAPlainGreeting() throws Exception {
        new EmailService(mailSender, "", "ARCADE", STORE, "smtp-user@gmail.com").sendWelcome("a@test.com", null, "es", null, null);

        assertThat((String) sentMessage().getContent()).contains("¡Hola!").doesNotContain("¡Hola, ");
    }

    @Test
    void firstUrlTakesTheFirstHttpOriginWithoutTrailingSlash() {
        assertThat(EmailService.firstUrl("https://a.test/,https://b.test")).isEqualTo("https://a.test");
        assertThat(EmailService.firstUrl(" http://localhost:5173 ")).isEqualTo("http://localhost:5173");
        assertThat(EmailService.firstUrl("*")).isNull();
        assertThat(EmailService.firstUrl("")).isNull();
        assertThat(EmailService.firstUrl(null)).isNull();
    }

    @Test
    void emptyFromFallsBackToSmtpUsername() throws Exception {
        new EmailService(mailSender, "", "ARCADE", STORE, "smtp-user@gmail.com").sendWelcome("a@test.com", "Ana", "es", null, null);

        assertThat(((InternetAddress) sentMessage().getFrom()[0]).getAddress()).isEqualTo("smtp-user@gmail.com");
    }

    @Test
    void mailFromIsTrimmed() throws Exception {
        new EmailService(mailSender, "  tienda@capstone.test 	", "ARCADE", STORE, "smtp-user@gmail.com")
                .sendWelcome("a@test.com", "Ana", "es", null, null);

        assertThat(((InternetAddress) sentMessage().getFrom()[0]).getAddress()).isEqualTo("tienda@capstone.test");
    }

    @Test
    void mailIsSentInTheRecipientsLanguage() throws Exception {
        new EmailService(mailSender, "", "ARCADE", STORE, "smtp-user@gmail.com").sendPaymentFailed("a@test.com", "Ana",
                3L, BigDecimal.TEN, "Tarjeta rechazada", "it");

        MimeMessage message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("Non siamo riusciti ad addebitare il tuo ordine #3");
        assertThat((String) message.getContent()).contains("<html lang=\"it\">", "Non siamo riusciti");
    }

    @Test
    void withoutSmtpCredentialsNothingIsSent() {
        JavaMailSender untouched = mock(JavaMailSender.class);
        new EmailService(untouched, "", "ARCADE", STORE, "").sendWelcome("a@test.com", "Ana", "es", null, null);
        verifyNoInteractions(untouched);
    }

    @Test
    void smtpFailureIsLoggedNotThrown() {
        doThrow(new MailSendException("Gmail caído")).when(mailSender).send(any(MimeMessage.class));
        var service = new EmailService(mailSender, "from@test.com", "ARCADE", STORE, "user@gmail.com");

        assertThatCode(() -> service.sendPaymentFailed("a@test.com", "Ana", 1L, BigDecimal.TEN, "Rechazada", "es"))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ reintentos y enlace de verificación

    private static final String TOKEN = "abc_DEF-123";

    private final List<Duration> pauses = new ArrayList<>();

    private EmailService retryingService() {
        return new EmailService(mailSender, "tienda@capstone.test", "ARCADE", STORE, "user@gmail.com", pauses::add);
    }

    private static MailSendException connectionFailure() {
        return new MailSendException("Couldn't connect to host smtp.gmail.com:587; timeout 15000",
                new SocketTimeoutException("connect timed out"));
    }

    @Test
    void retriesConnectionFailuresTwiceThenSends() {
        doThrow(connectionFailure()).doThrow(connectionFailure()).doNothing()
                .when(mailSender).send(any(MimeMessage.class));

        retryingService().sendWelcome("a@test.com", "Ana", "es", TOKEN, "ana");

        verify(mailSender, times(3)).send(any(MimeMessage.class));
        assertThat(pauses).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(6));
    }

    @Test
    void anAngusConnectExceptionCountsAsAConnectionFailure() {
        doThrow(new MailSendException("fallo", new MailConnectException("Couldn't connect to host"))).doNothing()
                .when(mailSender).send(any(MimeMessage.class));

        retryingService().sendWelcome("a@test.com", "Ana", "es", null, null);

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void givesUpAfterThreeConnectionFailuresWithoutThrowing() {
        doThrow(connectionFailure()).when(mailSender).send(any(MimeMessage.class));
        var service = retryingService();

        assertThatCode(() -> service.sendWelcome("a@test.com", "Ana", "es", TOKEN, "ana")).doesNotThrowAnyException();

        verify(mailSender, times(3)).send(any(MimeMessage.class));
        assertThat(pauses).hasSize(2);
    }

    @Test
    void doesNotRetryAuthenticationFailures() {
        doThrow(new MailAuthenticationException("535 credenciales")).when(mailSender).send(any(MimeMessage.class));

        retryingService().sendWelcome("a@test.com", "Ana", "es", TOKEN, "ana");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        assertThat(pauses).isEmpty();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void retryLogsTheAttemptWithAMaskedAddress(CapturedOutput output) {
        doThrow(connectionFailure()).doNothing().when(mailSender).send(any(MimeMessage.class));

        retryingService().sendWelcome("ana@test.com", "Ana", "es", null, null);

        assertThat(output.getAll()).contains("Reintento 2/3 del correo a a***@test.com").doesNotContain("ana@test.com");
    }

    // ------------------------------------------------------------------ API HTTP de Brevo (Render bloquea el SMTP)

    private final BrevoMailClient brevo = mock(BrevoMailClient.class);

    private EmailService brevoService(String smtpUsername) {
        when(brevo.enabled()).thenReturn(true);
        return new EmailService(mailSender, brevo, "tienda@gmail.com", "ARCADE", STORE, smtpUsername, pauses::add);
    }

    @Test
    void supportRequestViaBrevoCarriesTheReplyTo() {
        brevoService("user@gmail.com").sendSupportRequest("soporte@arcade-shop.com", "ana@gmail.com", "Ana", "ana",
                "Asunto", "Mensaje de prueba", "es");

        verify(brevo).send(eq("tienda@gmail.com"), eq("ARCADE"), eq("soporte@arcade-shop.com"),
                eq("[Assistenza ARCADE] Asunto"), any(), eq("ana@gmail.com"));
    }

    @Test
    void withABrevoKeyTheMailGoesThroughTheApiNotSmtp() {
        brevoService("user@gmail.com").sendWelcome("ana@test.com", "Ana", "it", TOKEN, "ana");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(brevo).send(eq("tienda@gmail.com"), eq("ARCADE"),
                eq("ana@test.com"),
                eq("Benvenuto su ARCADE! Conferma la tua email"), html.capture(), isNull());
        assertThat(html.getValue()).contains("/verify-email?token=" + TOKEN);
        verifyNoInteractions(mailSender);
    }

    @Test
    void brevoAloneEnablesMailEvenWithoutGmailCredentials() {
        brevoService("").sendPurchaseReceipt("ana@test.com", "Ana", 7L, new BigDecimal("9.99"), List.of(), "es");

        verify(brevo).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void brevoNetworkErrorsAreRetriedButARejectedKeyIsNot() {
        doThrow(new ResourceAccessException("timeout")).doNothing()
                .when(brevo).send(any(), any(), any(), any(), any(), any());
        var service = brevoService("user@gmail.com");

        service.sendWelcome("ana@test.com", "Ana", "es", null, null);
        verify(brevo, times(2)).send(any(), any(), any(), any(), any(), any());
        assertThat(pauses).containsExactly(Duration.ofSeconds(2));

        doThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "", null, null, null))
                .when(brevo).send(any(), any(), any(), any(), any(), any());
        assertThatCode(() -> service.sendWelcome("ana@test.com", "Ana", "es", null, null)).doesNotThrowAnyException();
        verify(brevo, times(3)).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void brevoAlsoSkipsReservedDomains() {
        brevoService("user@gmail.com").sendWelcome("demo_player@demo.capstone-steam.local", "Demo", "es", null, null);

        verify(brevo, never()).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsVerificationLinkOnlyWhenMailIsDisabled(CapturedOutput output) {
        new EmailService(mailSender, "", "ARCADE", STORE, "").sendWelcome("ana@test.com", "Ana", "es", TOKEN, "ana");
        assertThat(output.getAll()).contains(STORE + "/verify-email?token=" + TOKEN);
        verifyNoInteractions(mailSender);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void neverLogsTheTokenWhenMailIsEnabled(CapturedOutput output) {
        var service = retryingService();
        service.sendWelcome("ana@test.com", "Ana", "es", TOKEN, "ana");
        doThrow(connectionFailure()).when(mailSender).send(any(MimeMessage.class));
        service.sendWelcome("ana@test.com", "Ana", "es", TOKEN, "ana");
        service.sendWelcome("ana@example.com", "Ana", "es", TOKEN, "ana"); // dominio reservado: tampoco

        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    @Test
    void verificationMailCarriesTheLinkInTheRecipientsLanguage() throws Exception {
        retryingService().sendWelcome("a@test.com", "Ana", "en", TOKEN, "ana");

        MimeMessage message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("Welcome to ARCADE! Confirm your email");
        assertThat((String) message.getContent()).contains("href=\"" + STORE + "/verify-email?token=" + TOKEN + "\"");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void warnsWhenTheVerificationMailHasNoLinkBecauseThereIsNoStoreUrl(CapturedOutput output) throws Exception {
        new EmailService(mailSender, "tienda@capstone.test", "ARCADE", "", "user@gmail.com")
                .sendWelcome("ana@test.com", "Ana", "es", TOKEN, "ana");

        assertThat(output.getAll()).contains("sale sin enlace", "a***@test.com").doesNotContain(TOKEN);
    }

    @Test
    void tokenIsUrlEncodedInTheLink() {
        assertThat(EmailTemplates.welcome(new EmailTemplates.Brand("ARCADE", STORE), "Ana", "es", "a+b/c=", "ana").html())
                .contains("/verify-email?token=a%2Bb%2Fc%3D");
    }

    /** El único mensaje que el servicio ha entregado al sender. */
    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        return sent.getValue();
    }

    @Test
    void maskEmailKeepsFirstLetterAndDomain() {
        assertThat(EmailService.maskEmail("lucho@gmail.com")).isEqualTo("l***@gmail.com");
        assertThat(EmailService.maskEmail("a@b.test")).isEqualTo("a***@b.test");
        assertThat(EmailService.maskEmail(null)).isEqualTo("***");
        assertThat(EmailService.maskEmail("")).isEqualTo("***");
        assertThat(EmailService.maskEmail("sin-arroba")).isEqualTo("***");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsNeverContainTheFullRecipientAddress(CapturedOutput output) {
        var service = new EmailService(mailSender, "tienda@capstone.test", "ARCADE", STORE, "smtp-user@gmail.com");

        service.sendWelcome("ana@test.com", "Ana", "es", null, null);
        doThrow(new MailSendException("550 rejected: ana@test.com")).when(mailSender).send(any(MimeMessage.class));
        service.sendWelcome("ana@test.com", "Ana", "es", null, null);
        service.sendWelcome("demo@demo.capstone-steam.local", "Demo", "es", null, null);

        assertThat(output.getAll())
                .doesNotContain("ana@test.com", "demo@demo.capstone-steam.local")
                .contains("a***@test.com", "d***@demo.capstone-steam.local");
    }
}
