package com.capstone.steam.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.capstone.steam.notification.EmailTemplates.Brand;
import com.capstone.steam.notification.EmailTemplates.Email;
import com.capstone.steam.notification.event.OrderPaidEvent;
import com.capstone.steam.user.DeletionRequester;

/** Correos en el idioma del destinatario: es, en, it; cualquier otro valor cae en español. */
class EmailTemplatesI18nTest {

    private static final Brand BRAND = new Brand("ARCADE", "https://arcade.test");
    private static final BigDecimal TOTAL = new BigDecimal("1234.50");
    private static final List<OrderPaidEvent.Line> TWO_GAMES = List.of(
            new OrderPaidEvent.Line("Celeste", null, new BigDecimal("15.00")),
            new OrderPaidEvent.Line("Hades", null, new BigDecimal("1219.50")));

    // ------------------------------------------------------------------ asistencia

    @ParameterizedTest
    @CsvSource({
            "es, Hemos recibido tu mensaje, Copia de tu mensaje",
            "en, We have received your message, A copy of your message",
            "it, Abbiamo ricevuto il tuo messaggio, Copia del tuo messaggio",
            "fr, Hemos recibido tu mensaje, Copia de tu mensaje"})
    void supportConfirmationInEachLanguageWithAnEscapedCopyAndNoStoreButtons(String locale, String subject, String copy) {
        Email email = EmailTemplates.supportConfirmation(BRAND, "<i>Ana</i>", locale, "Asunto <script>");

        assertThat(email.subject()).isEqualTo(subject);
        assertThat(email.html()).contains(copy, "Asunto &lt;script&gt;", "&lt;i&gt;Ana&lt;/i&gt;")
                .doesNotContain("<script>", "<i>Ana</i>", "href=\"https://arcade.test/");
    }

    @Test
    void supportRequestEscapesEverythingTheUserTyped() {
        Email email = EmailTemplates.supportRequest(BRAND, "a@b.com", "<b>X</b>", "u<s>", "es", "s", "<img src=x onerror=1>\nlinea 2");

        assertThat(email.subject()).isEqualTo("[Assistenza ARCADE] s");
        assertThat(email.html()).contains("&lt;b&gt;X&lt;/b&gt;", "u&lt;s&gt;", "&lt;img src=x onerror=1&gt;", "es", "a@b.com")
                .doesNotContain("<b>X</b>", "<img", "<s>");
    }

    // ------------------------------------------------------------------ bienvenida

    @Test
    void welcomeInEnglish() {
        Email email = EmailTemplates.welcome(BRAND, "Ana", "en", null, null);

        assertThat(email.subject()).isEqualTo("Welcome to ARCADE!");
        assertThat(email.html()).contains("<html lang=\"en\">", "Hi, Ana!", "Account created",
                "Your ARCADE account is ready",
                "This is an automated email, please do not reply.")
                .doesNotContain("¡Hola", "Explorar el catálogo", "correo automático");
    }

    @Test
    void welcomeInItalian() {
        Email email = EmailTemplates.welcome(BRAND, "Ana", "it", null, null);

        assertThat(email.subject()).isEqualTo("Benvenuto su ARCADE!");
        assertThat(email.html()).contains("<html lang=\"it\">", "Ciao, Ana!", "Account creato",
                "Il tuo account ARCADE è pronto",
                "Questa è un'email automatica, non rispondere.")
                .doesNotContain("¡Hola", "correo automático");
    }

    @Test
    void welcomeInSpanishIsUnchanged() {
        Email email = EmailTemplates.welcome(BRAND, "Ana", "es", null, null);

        assertThat(email.subject()).isEqualTo("¡Bienvenido a ARCADE!");
        assertThat(email.html()).contains("<html lang=\"es\">", "¡Hola, Ana!",
                "Este es un correo automático, no respondas.")
                .doesNotContain("Explorar el cat&aacute;logo");
    }

    // ------------------------------------------------------------------ bienvenida con verificación

    @Test
    void welcomeWithTokenAsksToVerifyInSpanish() {
        Email email = EmailTemplates.welcome(BRAND, "Ana", "es", "abc_DEF-123", "ana");

        assertThat(email.subject()).isEqualTo("¡Bienvenido a ARCADE! Confirma tu email");
        assertThat(email.html()).contains("href=\"https://arcade.test/verify-email?token=abc_DEF-123\"",
                "Verificar mi email", "Confirma la cuenta «ana» de ARCADE",
                "El enlace caduca en 24 horas")
                .doesNotContain("/browse");
    }

    @Test
    void welcomeWithTokenInEnglishAndItalian() {
        Email en = EmailTemplates.welcome(BRAND, "Ana", "en", "tok", "ana");
        Email it = EmailTemplates.welcome(BRAND, "Ana", "it", "tok", "ana");

        assertThat(en.subject()).isEqualTo("Welcome to ARCADE! Confirm your email");
        assertThat(en.html()).contains("/verify-email?token=tok", "Verify my email", "24 hours",
                "Confirm the account “ana” on ARCADE")
                .doesNotContain("Verificar mi email");
        assertThat(it.subject()).isEqualTo("Benvenuto su ARCADE! Conferma la tua email");
        assertThat(it.html()).contains("/verify-email?token=tok", "Verifica la mia email", "24 ore",
                "Conferma l’account «ana» su ARCADE");
    }

    @Test
    void verificationMailNamesTheAccountEscaped() {
        Email email = EmailTemplates.welcome(BRAND, "Ana", "es", "tok", "<i>x</i>");

        assertThat(email.html()).contains("&lt;i&gt;x&lt;/i&gt;").doesNotContain("<i>x</i>");
    }

    @Test
    void welcomeWithoutTokenIsTheClassicOne() {
        Email email = EmailTemplates.welcome(BRAND, "Ana", "es", null, null);

        assertThat(email.subject()).isEqualTo("¡Bienvenido a ARCADE!");
        assertThat(email.html()).doesNotContain("verify-email", "href=\"https://arcade.test/");
    }

    /** Solo el correo de verificación lleva botón: los demás no enlazan a páginas de la tienda. */
    @Test
    void mailsHaveNoButtonsBackToTheStore() {
        for (String locale : new String[] {"es", "en", "it"}) {
            String[] html = {
                EmailTemplates.welcome(BRAND, "Ana", locale, null, null).html(),
                EmailTemplates.purchaseReceipt(BRAND, "Ana", 7L, BigDecimal.TEN,
                        List.of(new OrderPaidEvent.Line("Celeste", null, BigDecimal.TEN)), locale).html(),
                EmailTemplates.paymentFailed(BRAND, "Ana", 7L, BigDecimal.TEN, "card", locale).html(),
                EmailTemplates.gameOnSale(BRAND, "Ana", "Celeste", null, BigDecimal.TEN,
                        BigDecimal.ONE, 90, locale).html(),
            };
            for (String h : html) {
                assertThat(h).doesNotContain("href=\"https://arcade.test/", "/library", "/cart", "/browse",
                        "/games/");
            }
        }
    }

    // ------------------------------------------------------------------ recibo

    @Test
    void receiptInEnglish() {
        Email email = EmailTemplates.purchaseReceipt(BRAND, "Ana", 42L, TOTAL, TWO_GAMES, "en");

        assertThat(email.subject()).isEqualTo("Receipt for your purchase #42");
        assertThat(email.html()).contains("<html lang=\"en\">", "Thanks for your purchase, Ana!",
                "These games are now in your library:", "Order #42 · Paid".replace("·", "&middot;"),
                ">Total<", "€1,234.50", "Order #42 paid: &euro;1,234.50.") // el preheader va escapado
                .doesNotContain("Pedido", "biblioteca");
    }

    @Test
    void receiptInItalian() {
        Email email = EmailTemplates.purchaseReceipt(BRAND, "Ana", 42L, TOTAL, TWO_GAMES.subList(0, 1), "it");

        assertThat(email.subject()).isEqualTo("Ricevuta del tuo acquisto #42");
        assertThat(nbsp(email.html())).contains("<html lang=\"it\">", "Grazie per il tuo acquisto, Ana!",
                "Questo gioco è ora nella tua libreria:", ">Totale<", "1.234,50 €")
                .doesNotContain("Pedido", "biblioteca");
    }

    // ------------------------------------------------------------------ pago rechazado

    @Test
    void paymentFailedInEnglishKeepsTheGatewayReasonAsIs() {
        Email email = EmailTemplates.paymentFailed(BRAND, "Ana", 7L, TOTAL, "Tarjeta rechazada <x>", "en");

        assertThat(email.subject()).isEqualTo("We couldn't charge your order #7");
        assertThat(email.html()).contains("<html lang=\"en\">", "Hi, Ana.", "The payment of <strong",
                "€1,234.50</strong> was declined:",
                "The games are still in your cart: you can try again with another payment method.",
                "Tarjeta rechazada &lt;x&gt;") // el motivo lo da la pasarela y no se traduce
                .doesNotContain("Volver al carrito", "<x>");
    }

    @Test
    void paymentFailedInItalian() {
        Email email = EmailTemplates.paymentFailed(BRAND, "Ana", 7L, TOTAL, "Rechazada", "it");

        assertThat(email.subject()).isEqualTo("Non siamo riusciti ad addebitare il tuo ordine #7");
        assertThat(nbsp(email.html())).contains("<html lang=\"it\">", "Ciao, Ana.", "Il pagamento di <strong",
                "1.234,50 €</strong> è stato rifiutato:");
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
        assertThat(email.html()).contains("<html lang=\"es\">", "No hemos podido cobrar");
    }

    @Test
    void localeCodeIsCaseAndSpaceInsensitive() {
        assertThat(MailTexts.of(" EN ")).isSameAs(MailTexts.EN);
        assertThat(MailTexts.of("It")).isSameAs(MailTexts.IT);
    }

    @ParameterizedTest
    @ValueSource(strings = { "es", "en", "it" })
    void userDataIsEscapedInEveryLanguage(String locale) {
        Email welcome = EmailTemplates.welcome(BRAND, "<b>Hacker</b>", locale, null, null);
        Email receipt = EmailTemplates.purchaseReceipt(BRAND, "<b>Hacker</b>", 1L, BigDecimal.TEN,
                List.of(new OrderPaidEvent.Line("<script>x</script>", "javascript:alert(1)", BigDecimal.TEN)), locale);

        assertThat(welcome.html() + receipt.html()).doesNotContain("<b>Hacker</b>", "<script>", "javascript:", "<img")
                .contains("&lt;b&gt;Hacker&lt;/b&gt;", "&lt;script&gt;x&lt;/script&gt;");
    }

    /** Los formatos de moneda de es/it usan espacio duro antes de «€»; se normaliza para comparar. */
    private static String nbsp(String text) {
        return text.replace(' ', ' ').replace(' ', ' ');
    }

    // ------------------------------------------------------------------ baja de la cuenta

    /** 2026-11-21 23:30 UTC ya es 22 de noviembre en Roma (UTC+1): la fecha se da en esa zona. */
    private static final Instant SCHEDULED_AT = Instant.parse("2026-11-21T23:30:00Z");

    @Test
    void deletionScheduledBySelfOffersRecoveryInThreeLanguages() {
        Email es = EmailTemplates.deletionScheduled(BRAND, "Ana", "es", SCHEDULED_AT, DeletionRequester.SELF);
        Email en = EmailTemplates.deletionScheduled(BRAND, "Ana", "en", SCHEDULED_AT, DeletionRequester.SELF);
        Email it = EmailTemplates.deletionScheduled(BRAND, "Ana", "it", SCHEDULED_AT, DeletionRequester.SELF);

        assertThat(es.html()).contains("<html lang=\"es\">", "Tu cuenta se eliminará el 22 de noviembre de 2026.",
                "Si cambias de idea, inicia sesión antes de esa fecha y pulsa Recuperar mi cuenta.");
        assertThat(en.html()).contains("<html lang=\"en\">", "Your account will be deleted on 22 November 2026.",
                "If you change your mind, sign in before that date and press Recover my account.");
        assertThat(it.html()).contains("<html lang=\"it\">", "Il tuo account verrà eliminato il 22 novembre 2026.",
                "Se cambi idea, accedi prima di quella data e premi Recupera il mio account.");
        assertThat(es.subject()).isNotBlank().isNotEqualTo(en.subject());
        assertThat(en.subject()).isNotEqualTo(it.subject());
    }

    @Test
    void deletionScheduledByAdminAsksToReplyInThreeLanguages() {
        Email es = EmailTemplates.deletionScheduled(BRAND, "Ana", "es", SCHEDULED_AT, DeletionRequester.ADMIN);
        Email en = EmailTemplates.deletionScheduled(BRAND, "Ana", "en", SCHEDULED_AT, DeletionRequester.ADMIN);
        Email it = EmailTemplates.deletionScheduled(BRAND, "Ana", "it", SCHEDULED_AT, DeletionRequester.ADMIN);

        assertThat(es.html()).contains("Un administrador ha programado la eliminación de tu cuenta para el 22 de "
                + "noviembre de 2026. Si crees que es un error, contesta a este correo.")
                .doesNotContain("Recuperar mi cuenta");
        assertThat(en.html()).contains("An administrator has scheduled the deletion of your account for 22 "
                + "November 2026. If you think this is a mistake, reply to this email.");
        assertThat(it.html()).contains("Un amministratore ha programmato l’eliminazione del tuo account per il 22 "
                + "novembre 2026. Se pensi che sia un errore, rispondi a questa email.");
    }

    @Test
    void deletionCancelledAndDeletedInThreeLanguages() {
        assertThat(EmailTemplates.deletionCancelled(BRAND, "Ana", "es").html())
                .contains("Tu cuenta vuelve a estar activa.");
        assertThat(EmailTemplates.deletionCancelled(BRAND, "Ana", "en").html())
                .contains("Your account is active again.");
        assertThat(EmailTemplates.deletionCancelled(BRAND, "Ana", "it").html())
                .contains("Il tuo account è di nuovo attivo.");
        assertThat(EmailTemplates.accountDeleted(BRAND, "Ana", "es").html()).contains(
                "Tu cuenta y tus datos personales se han eliminado. Conservamos los pedidos por obligación legal, "
                        + "sin datos que te identifiquen.");
        assertThat(EmailTemplates.accountDeleted(BRAND, "Ana", "en").html()).contains(
                "Your account and personal data have been deleted. We keep orders as required by law, without any "
                        + "data that identifies you.");
        assertThat(EmailTemplates.accountDeleted(BRAND, "Ana", "it").html()).contains(
                "Il tuo account e i tuoi dati personali sono stati eliminati. Conserviamo gli ordini per obbligo "
                        + "di legge, senza dati che ti identifichino.");
    }

    @Test
    void deletionMailsInThreeLanguagesWithoutStoreButtons() {
        for (String locale : new String[] {"es", "en", "it"}) {
            String[] html = {
                EmailTemplates.deletionScheduled(BRAND, "Ana", locale, SCHEDULED_AT, DeletionRequester.SELF).html(),
                EmailTemplates.deletionScheduled(BRAND, "Ana", locale, SCHEDULED_AT, DeletionRequester.ADMIN).html(),
                EmailTemplates.deletionCancelled(BRAND, "Ana", locale).html(),
                EmailTemplates.accountDeleted(BRAND, "Ana", locale).html(),
            };
            for (String h : html) {
                assertThat(h).contains("<html lang=\"" + locale + "\">")
                        .doesNotContain("/library", "/cart", "/browse", "/games/", "/verify-email");
                // el único enlace es el logotipo de la marca
                assertThat(h.split("<a ", -1)).hasSize(2);
                assertThat(h).contains("<a href=\"https://arcade.test\"");
            }
        }
    }

    @Test
    void deletionMailsWithoutDisplayNameGreetWithoutIt() {
        assertThat(EmailTemplates.deletionCancelled(BRAND, null, "es").html()).contains("Hola.").doesNotContain("null");
        assertThat(EmailTemplates.accountDeleted(BRAND, " ", "en").html()).contains("Hi.").doesNotContain("null");
        assertThat(EmailTemplates.deletionScheduled(BRAND, null, "it", SCHEDULED_AT, DeletionRequester.SELF).html())
                .contains("Ciao.").doesNotContain("null");
    }

    @Test
    void deletionMailsEscapeTheDisplayName() {
        assertThat(EmailTemplates.accountDeleted(BRAND, "<b>X</b>", "es").html())
                .contains("&lt;b&gt;X&lt;/b&gt;").doesNotContain("<b>X</b>");
    }

    // ------------------------------------------------------------------ oferta en la wishlist

    @Test
    void gameOnSaleInItalianEscapesTheTitle() {
        Email email = EmailTemplates.gameOnSale(BRAND, "Ana", "Tom & <Jerry>",
                "https://cdn.test/cover.jpg", new BigDecimal("18.99"), new BigDecimal("4.74"), 75, "it");

        assertThat(email.subject()).isEqualTo("Tom & <Jerry> è in offerta: −75%");
        assertThat(email.html()).contains("<html lang=\"it\">", "Tom &amp; &lt;Jerry&gt; è in offerta!",
                "La tua lista dei desideri &middot; &minus;75%", "4,74",
                "https://cdn.test/cover.jpg")
                .doesNotContain("<Jerry>");
    }

    @Test
    void gameOnSaleFallsBackToSpanish() {
        Email email = EmailTemplates.gameOnSale(BRAND, null, "Celeste", "javascript:alert(1)",
                new BigDecimal("18.99"), new BigDecimal("4.74"), 75, "fr");

        assertThat(email.subject()).isEqualTo("Celeste está en oferta: −75%");
        assertThat(email.html()).contains("Celeste").doesNotContain("javascript:");
    }
}
