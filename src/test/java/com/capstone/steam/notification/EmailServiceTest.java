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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

class EmailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @Test
    void sendsHtmlReceiptWithEscapedUserData() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        var service = new EmailService(mailSender, "tienda@capstone.test", "smtp-user@gmail.com");

        service.sendPurchaseReceipt("player@test.com", "<b>Hacker</b>", 42L, new BigDecimal("39.99"),
                List.of("Celeste", "Hades"));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        assertThat(message.getSubject()).isEqualTo("Recibo de tu compra #42");
        assertThat(((InternetAddress) message.getAllRecipients()[0]).getAddress()).isEqualTo("player@test.com");
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("tienda@capstone.test");

        String html = (String) message.getContent();
        assertThat(html).contains("&lt;b&gt;Hacker&lt;/b&gt;").doesNotContain("<b>Hacker</b>");
        assertThat(html).contains("<li>Celeste</li>", "<li>Hades</li>", "39,99");
    }

    @Test
    void emptyFromFallsBackToSmtpUsername() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        new EmailService(mailSender, "", "smtp-user@gmail.com").sendWelcome("a@test.com", "Ana");

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(((InternetAddress) sent.getValue().getFrom()[0]).getAddress()).isEqualTo("smtp-user@gmail.com");
    }

    @Test
    void withoutSmtpCredentialsNothingIsSent() {
        new EmailService(mailSender, "", "").sendWelcome("a@test.com", "Ana");
        verifyNoInteractions(mailSender);
    }

    @Test
    void smtpFailureIsLoggedNotThrown() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(new MailSendException("Gmail caído")).when(mailSender).send(any(MimeMessage.class));
        var service = new EmailService(mailSender, "from@test.com", "user@gmail.com");

        assertThatCode(() -> service.sendPaymentFailed("a@test.com", "Ana", 1L, BigDecimal.TEN, "Rechazada"))
                .doesNotThrowAnyException();
    }
}
