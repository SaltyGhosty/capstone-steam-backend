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
        // Bienvenida con verificación de email
        String verifySubject,
        String verifyPreheader,
        String verifyBody,
        String verifyButton,
        String verifyExpiry,
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
        String failedButton,
        // Oferta en la wishlist
        String saleSubject,
        String salePreheader,
        String saleEyebrow,
        String saleHeading,
        String saleBody,
        String saleButton) {

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
            "¡Bienvenido a %s! Confirma tu email",
            "Confirma tu email para empezar a usar tu cuenta.",
            "Confirma la cuenta «%s» de %s: pulsa el botón para verificar tu email y poder iniciar sesión.",
            "Verificar mi email",
            "El enlace caduca en 24 horas. Si no has creado esta cuenta, ignora este correo.",
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
            "Volver al carrito",
            "%s está en oferta: −%d%%",
            "Un juego de tu lista de deseos ha bajado de precio.",
            "Tu lista de deseos · −%d%%",
            "¡%s está en oferta!",
            "Uno de los juegos que guardaste en tu lista de deseos acaba de bajar de precio:",
            "Ver el juego");

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
            "Welcome to %s! Confirm your email",
            "Confirm your email to start using your account.",
            "Confirm the account “%s” on %s: press the button to verify your email and be able to sign in.",
            "Verify my email",
            "The link expires in 24 hours. If you did not create this account, ignore this email.",
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
            "Back to cart",
            "%s is on sale: −%d%%",
            "A game on your wishlist just got cheaper.",
            "Your wishlist · −%d%%",
            "%s is on sale!",
            "One of the games you saved to your wishlist just dropped in price:",
            "See the game");

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
            "Benvenuto su %s! Conferma la tua email",
            "Conferma la tua email per iniziare a usare il tuo account.",
            "Conferma l’account «%s» su %s: premi il pulsante per verificare la tua email e poter accedere.",
            "Verifica la mia email",
            "Il link scade tra 24 ore. Se non hai creato tu questo account, ignora questa email.",
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
            "Torna al carrello",
            "%s è in offerta: −%d%%",
            "Un gioco della tua lista dei desideri è sceso di prezzo.",
            "La tua lista dei desideri · −%d%%",
            "%s è in offerta!",
            "Uno dei giochi che hai salvato nella tua lista dei desideri è appena sceso di prezzo:",
            "Vedi il gioco");

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
