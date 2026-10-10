package com.capstone.steam.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.capstone.steam.notification.event.OrderPaidEvent;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

class EmailServiceTest {

    private static final String STORE = "https://arcade.test";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
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
    void uppercaseHttpSchemeIsAccepted() throws Exception {
        receipt(List.of(line("Celeste", "HTTP://img.test/c.jpg", "15.00")), "15.00");

        assertThat((String) sentMessage().getContent()).contains("<img src=\"HTTP://img.test/c.jpg\"");
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
    void safeImageUrlOnlyAcceptsHttpAndHttps() {
        assertThat(EmailTemplates.safeImageUrl("https://a.test/x.png")).isEqualTo("https://a.test/x.png");
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
        assertThat(EmailTemplates.safeImageUrl("http://a.test/x.png")).isEqualTo("http://a.test/x.png");
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
                .sendWelcome("a@test.com", "Ana", "es");

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
                .sendWelcome("a@test.com", "Ana", "es");

        MimeMessage message = sentMessage();
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("ARCADE");
        assertThat(message.getSubject()).isEqualTo("¡Bienvenido a ARCADE!");
    }

    @Test
    void buttonsLinkToTheStore() throws Exception {
        var service = new EmailService(mailSender, "", "ARCADE", "https://arcade.test/, http://localhost:5173",
                "smtp-user@gmail.com");

        service.sendPurchaseReceipt("a@test.com", "Ana", 7L, BigDecimal.TEN, List.of(line("Celeste", null, "10.00")),
                "es");

        String html = (String) sentMessage().getContent();
        assertThat(html).contains("href=\"https://arcade.test/library\"", "Este juego ya está en tu biblioteca");
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
        new EmailService(mailSender, "", "ARCADE", STORE, "smtp-user@gmail.com").sendWelcome("a@test.com", null, "es");

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
        new EmailService(mailSender, "", "ARCADE", STORE, "smtp-user@gmail.com").sendWelcome("a@test.com", "Ana", "es");

        assertThat(((InternetAddress) sentMessage().getFrom()[0]).getAddress()).isEqualTo("smtp-user@gmail.com");
    }

    @Test
    void mailFromIsTrimmed() throws Exception {
        new EmailService(mailSender, "  tienda@capstone.test 	", "ARCADE", STORE, "smtp-user@gmail.com")
                .sendWelcome("a@test.com", "Ana", "es");

        assertThat(((InternetAddress) sentMessage().getFrom()[0]).getAddress()).isEqualTo("tienda@capstone.test");
    }

    @Test
    void mailIsSentInTheRecipientsLanguage() throws Exception {
        new EmailService(mailSender, "", "ARCADE", STORE, "smtp-user@gmail.com").sendPaymentFailed("a@test.com", "Ana",
                3L, BigDecimal.TEN, "Tarjeta rechazada", "it");

        MimeMessage message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("Non siamo riusciti ad addebitare il tuo ordine #3");
        assertThat((String) message.getContent()).contains("<html lang=\"it\">", "Torna al carrello");
    }

    @Test
    void withoutSmtpCredentialsNothingIsSent() {
        JavaMailSender untouched = mock(JavaMailSender.class);
        new EmailService(untouched, "", "ARCADE", STORE, "").sendWelcome("a@test.com", "Ana", "es");
        verifyNoInteractions(untouched);
    }

    @Test
    void smtpFailureIsLoggedNotThrown() {
        doThrow(new MailSendException("Gmail caído")).when(mailSender).send(any(MimeMessage.class));
        var service = new EmailService(mailSender, "from@test.com", "ARCADE", STORE, "user@gmail.com");

        assertThatCode(() -> service.sendPaymentFailed("a@test.com", "Ana", 1L, BigDecimal.TEN, "Rechazada", "es"))
                .doesNotThrowAnyException();
    }

    /** El único mensaje que el servicio ha entregado al sender. */
    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        return sent.getValue();
    }
}
