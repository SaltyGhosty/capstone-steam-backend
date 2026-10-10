package com.capstone.steam.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.capstone.steam.notification.EmailTemplates.Brand;
import com.capstone.steam.notification.EmailTemplates.Email;
import com.capstone.steam.notification.event.OrderPaidEvent;

/** Correos en el idioma del destinatario: es, en, it; cualquier otro valor cae en español. */
class EmailTemplatesI18nTest {

    private static final Brand BRAND = new Brand("ARCADE", "https://arcade.test");
    private static final BigDecimal TOTAL = new BigDecimal("1234.50");
    private static final List<OrderPaidEvent.Line> TWO_GAMES = List.of(
            new OrderPaidEvent.Line("Celeste", null, new BigDecimal("15.00")),
            new OrderPaidEvent.Line("Hades", null, new BigDecimal("1219.50")));

    // ------------------------------------------------------------------ bienvenida

    @Test
    void welcomeInEnglish() {
        Email email = EmailTemplates.welcome(BRAND, "Ana", "en");

        assertThat(email.subject()).isEqualTo("Welcome to ARCADE!");
        assertThat(email.html()).contains("<html lang=\"en\">", "Hi, Ana!", "Account created",
                "Your ARCADE account is ready", "Explore the catalogue",
                "This is an automated email, please do not reply.")
                .doesNotContain("¡Hola", "Explorar el catálogo", "correo automático");
    }

    @Test
    void welcomeInItalian() {
        Email email = EmailTemplates.welcome(BRAND, "Ana", "it");

        assertThat(email.subject()).isEqualTo("Benvenuto su ARCADE!");
        assertThat(email.html()).contains("<html lang=\"it\">", "Ciao, Ana!", "Account creato",
                "Il tuo account ARCADE è pronto", "Esplora il catalogo",
                "Questa è un'email automatica, non rispondere.")
                .doesNotContain("¡Hola", "correo automático");
    }

    @Test
    void welcomeInSpanishIsUnchanged() {
        Email email = EmailTemplates.welcome(BRAND, "Ana", "es");

        assertThat(email.subject()).isEqualTo("¡Bienvenido a ARCADE!");
        assertThat(email.html()).contains("<html lang=\"es\">", "¡Hola, Ana!", "Explorar el cat&aacute;logo", // botones y eyebrow van escapados
                "Este es un correo automático, no respondas.");
    }

    // ------------------------------------------------------------------ recibo

    @Test
    void receiptInEnglish() {
        Email email = EmailTemplates.purchaseReceipt(BRAND, "Ana", 42L, TOTAL, TWO_GAMES, "en");

        assertThat(email.subject()).isEqualTo("Receipt for your purchase #42");
        assertThat(email.html()).contains("<html lang=\"en\">", "Thanks for your purchase, Ana!",
                "These games are now in your library:", "Order #42 · Paid".replace("·", "&middot;"),
                ">Total<", "Go to my library", "€1,234.50", "Order #42 paid: &euro;1,234.50.") // el preheader va escapado
                .doesNotContain("Pedido", "biblioteca");
    }

    @Test
    void receiptInItalian() {
        Email email = EmailTemplates.purchaseReceipt(BRAND, "Ana", 42L, TOTAL, TWO_GAMES.subList(0, 1), "it");

        assertThat(email.subject()).isEqualTo("Ricevuta del tuo acquisto #42");
        assertThat(nbsp(email.html())).contains("<html lang=\"it\">", "Grazie per il tuo acquisto, Ana!",
                "Questo gioco è ora nella tua libreria:", ">Totale<", "Vai alla mia libreria", "1.234,50 €")
                .doesNotContain("Pedido", "biblioteca");
    }

    // ------------------------------------------------------------------ pago rechazado

    @Test
    void paymentFailedInEnglishKeepsTheGatewayReasonAsIs() {
        Email email = EmailTemplates.paymentFailed(BRAND, "Ana", 7L, TOTAL, "Tarjeta rechazada <x>", "en");

        assertThat(email.subject()).isEqualTo("We couldn't charge your order #7");
        assertThat(email.html()).contains("<html lang=\"en\">", "Hi, Ana.", "The payment of <strong",
                "€1,234.50</strong> was declined:", "Back to cart",
                "The games are still in your cart: you can try again with another payment method.",
                "Tarjeta rechazada &lt;x&gt;") // el motivo lo da la pasarela y no se traduce
                .doesNotContain("Volver al carrito", "<x>");
    }

    @Test
    void paymentFailedInItalian() {
        Email email = EmailTemplates.paymentFailed(BRAND, "Ana", 7L, TOTAL, "Rechazada", "it");

        assertThat(email.subject()).isEqualTo("Non siamo riusciti ad addebitare il tuo ordine #7");
        assertThat(nbsp(email.html())).contains("<html lang=\"it\">", "Ciao, Ana.", "Il pagamento di <strong",
                "1.234,50 €</strong> è stato rifiutato:", "Torna al carrello");
    }

    // ------------------------------------------------------------------ dinero, fallback, seguridad

    @ParameterizedTest
    @CsvSource({ "es, 39.99, '39,99 €'", "it, 39.99, '39,99 €'", "en, 39.99, €39.99", "it, 1234.5, '1.234,50 €'",
            "en, 1234.5, '€1,234.50'" })
    void moneyIsEuroFormattedPerLanguage(String locale, String amount, String expected) {
        assertThat(nbsp(EmailTemplates.money(new BigDecimal(amount), MailTexts.of(locale)))).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "  ", "fr", "en-US", "xx" })
    void unknownOrMissingLocaleFallsBackToSpanish(String locale) {
        assertThat(MailTexts.of(locale)).isSameAs(MailTexts.ES);

        Email email = EmailTemplates.paymentFailed(BRAND, "Ana", 7L, TOTAL, "Rechazada", locale);
        assertThat(email.subject()).isEqualTo("No hemos podido cobrar tu pedido #7");
        assertThat(email.html()).contains("<html lang=\"es\">", "Volver al carrito");
    }

    @Test
    void localeCodeIsCaseAndSpaceInsensitive() {
        assertThat(MailTexts.of(" EN ")).isSameAs(MailTexts.EN);
        assertThat(MailTexts.of("It")).isSameAs(MailTexts.IT);
    }

    @ParameterizedTest
    @ValueSource(strings = { "es", "en", "it" })
    void userDataIsEscapedInEveryLanguage(String locale) {
        Email welcome = EmailTemplates.welcome(BRAND, "<b>Hacker</b>", locale);
        Email receipt = EmailTemplates.purchaseReceipt(BRAND, "<b>Hacker</b>", 1L, BigDecimal.TEN,
                List.of(new OrderPaidEvent.Line("<script>x</script>", "javascript:alert(1)", BigDecimal.TEN)), locale);

        assertThat(welcome.html() + receipt.html()).doesNotContain("<b>Hacker</b>", "<script>", "javascript:", "<img")
                .contains("&lt;b&gt;Hacker&lt;/b&gt;", "&lt;script&gt;x&lt;/script&gt;");
    }

    /** Los formatos de moneda de es/it usan espacio duro antes de «€»; se normaliza para comparar. */
    private static String nbsp(String text) {
        return text.replace(' ', ' ').replace(' ', ' ');
    }
}
