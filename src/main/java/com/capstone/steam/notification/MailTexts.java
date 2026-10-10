package com.capstone.steam.notification;

import java.util.Locale;

/**
 * Textos de los correos en cada idioma que admite {@code users.locale}: es, en, it.
 *
 * <p>Un record con una constante por idioma, en lugar de {@code MessageSource} con ficheros
 * {@code .properties}: el compilador obliga a que cada idioma tenga todos los textos, no hay
 * problemas de codificación ni claves mal escritas, y las plantillas siguen siendo métodos
 * estáticos sin dependencias de Spring.
 *
 * <p>Los textos son constantes nuestras y se insertan en el HTML tal cual (pueden llevar «¡», tildes
 * y apóstrofos). Los huecos {@code %s}/{@code %d} solo reciben datos ya escapados o generados por la
 * aplicación (número de pedido, importe formateado).
 *
 * @param lang          valor de {@code <html lang>}
 * @param moneyLocale   formato del importe en euros (en-IE para que el inglés siga siendo €)
 * @param footer        pie común, tras el nombre de la tienda
 */
record MailTexts(
        String lang,
        Locale moneyLocale,
        String footer,
        // Bienvenida
        String welcomeSubject,
        String welcomePreheader,
        String welcomeEyebrow,
        String welcomeHello,
        String welcomeBody,
        String welcomeButton,
        // Recibo
        String receiptSubject,
        String receiptPreheader,
        String receiptEyebrow,
        String receiptThanks,
        String receiptIntroOne,
        String receiptIntroMany,
        String total,
        String receiptButton,
        // Pago rechazado
        String failedSubject,
        String failedPreheader,
        String failedEyebrow,
        String failedHeading,
        String failedHello,
        String failedIntro,
        String failedRetry,
        String failedButton) {

    static final MailTexts ES = new MailTexts(
            "es",
            Locale.forLanguageTag("es-ES"),
            "Este es un correo automático, no respondas.",
            "¡Bienvenido a %s!",
            "Tu cuenta ya está lista. Empieza por el catálogo.",
            "Cuenta creada",
            "¡Hola",
            "Tu cuenta de %s ya está lista. Explora el catálogo, guarda juegos en tu lista de deseos y empieza tu "
                    + "biblioteca.",
            "Explorar el catálogo",
            "Recibo de tu compra #%d",
            "Pedido #%d pagado: %s.",
            "Pedido #%d · Pagado",
            "¡Gracias por tu compra",
            "Este juego ya está en tu biblioteca:",
            "Estos juegos ya están en tu biblioteca:",
            "Total",
            "Ir a mi biblioteca",
            "No hemos podido cobrar tu pedido #%d",
            "El pago de tu pedido #%d ha sido rechazado. Los juegos siguen en tu carrito.",
            "Pedido #%d · Pago rechazado",
            "No hemos podido cobrar tu pedido",
            "Hola",
            "El pago de %s ha sido rechazado:",
            "Los juegos siguen en tu carrito: puedes volver a intentarlo con otro método de pago.",
            "Volver al carrito");

    static final MailTexts EN = new MailTexts(
            "en",
            Locale.forLanguageTag("en-IE"),
            "This is an automated email, please do not reply.",
            "Welcome to %s!",
            "Your account is ready. Start with the catalogue.",
            "Account created",
            "Hi",
            "Your %s account is ready. Explore the catalogue, save games to your wishlist and start your library.",
            "Explore the catalogue",
            "Receipt for your purchase #%d",
            "Order #%d paid: %s.",
            "Order #%d · Paid",
            "Thanks for your purchase",
            "This game is now in your library:",
            "These games are now in your library:",
            "Total",
            "Go to my library",
            "We couldn't charge your order #%d",
            "The payment for your order #%d was declined. The games are still in your cart.",
            "Order #%d · Payment declined",
            "We couldn't charge your order",
            "Hi",
            "The payment of %s was declined:",
            "The games are still in your cart: you can try again with another payment method.",
            "Back to cart");

    static final MailTexts IT = new MailTexts(
            "it",
            Locale.forLanguageTag("it-IT"),
            "Questa è un'email automatica, non rispondere.",
            "Benvenuto su %s!",
            "Il tuo account è pronto. Inizia dal catalogo.",
            "Account creato",
            "Ciao",
            "Il tuo account %s è pronto. Esplora il catalogo, salva i giochi nella tua lista dei desideri e inizia "
                    + "la tua libreria.",
            "Esplora il catalogo",
            "Ricevuta del tuo acquisto #%d",
            "Ordine #%d pagato: %s.",
            "Ordine #%d · Pagato",
            "Grazie per il tuo acquisto",
            "Questo gioco è ora nella tua libreria:",
            "Questi giochi sono ora nella tua libreria:",
            "Totale",
            "Vai alla mia libreria",
            "Non siamo riusciti ad addebitare il tuo ordine #%d",
            "Il pagamento del tuo ordine #%d è stato rifiutato. I giochi sono ancora nel tuo carrello.",
            "Ordine #%d · Pagamento rifiutato",
            "Non siamo riusciti ad addebitare il tuo ordine",
            "Ciao",
            "Il pagamento di %s è stato rifiutato:",
            "I giochi sono ancora nel tuo carrello: puoi riprovare con un altro metodo di pagamento.",
            "Torna al carrello");

    /** Textos del idioma dado. {@code null}, vacío o desconocido ("fr", "en-US"...) → español. */
    static MailTexts of(String locale) {
        if (locale == null) {
            return ES;
        }
        return switch (locale.trim().toLowerCase(Locale.ROOT)) {
            case "en" -> EN;
            case "it" -> IT;
            default -> ES;
        };
    }
}
