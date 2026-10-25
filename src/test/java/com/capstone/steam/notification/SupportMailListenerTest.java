package com.capstone.steam.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.capstone.steam.notification.event.SupportRequestEvent;

class SupportMailListenerTest {

    private final EmailService emails = mock(EmailService.class);
    private final SupportRequestEvent event = new SupportRequestEvent("ana@gmail.com", "Ana", "ana", "Asunto", "Mensaje largo", "it");

    @Test
    void sendsTheRequestToSupportAndTheConfirmationToTheUser() {
        new MailListener(emails, "soporte@arcade.test").onSupportRequest(event);

        verify(emails).sendSupportRequest("soporte@arcade.test", "ana@gmail.com", "Ana", "ana", "Asunto", "Mensaje largo", "it");
        verify(emails).sendSupportConfirmation("ana@gmail.com", "Ana", "Asunto", "it");
    }

    @Test
    void withoutASupportAddressOnlyTheConfirmationIsSent() {
        new MailListener(emails, "  ").onSupportRequest(event);

        verify(emails, never()).sendSupportRequest(anyString(), anyString(), any(), any(), anyString(), anyString(), any());
        verify(emails).sendSupportConfirmation("ana@gmail.com", "Ana", "Asunto", "it");
    }
}
