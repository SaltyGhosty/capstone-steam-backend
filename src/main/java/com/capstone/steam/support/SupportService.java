package com.capstone.steam.support;

import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.i18n.Messages;
import com.capstone.steam.notification.event.SupportRequestEvent;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

/**
 * Recibe el mensaje del formulario de asistencia y lo convierte en un evento de correo. No guarda nada en la base de
 * datos: el mensaje solo existe en el buzón de soporte. Sin transacción.
 */
@Service
public class SupportService {

    private final UserRepository users;
    private final SupportRateLimiter emailLimiter;
    private final ApplicationEventPublisher events;

    public SupportService(UserRepository users, SupportRateLimiter emailLimiter, ApplicationEventPublisher events) {
        this.users = users;
        this.emailLimiter = emailLimiter;
        this.events = events;
    }

    /** @param userId id de la sesión, o null si quien escribe es anónimo */
    public void submit(Long userId, SupportRequest request) {
        User user = userId == null ? null : users.findById(userId).orElse(null);
        String email = user != null ? user.getEmail() : request.email();
        if (email == null) {
            throw new BadRequestException("support.emailRequired");
        }
        emailLimiter.check(email.toLowerCase(Locale.ROOT));
        String locale = user != null ? user.getLocale() : Messages.currentLocale().getLanguage();
        events.publishEvent(new SupportRequestEvent(email, user == null ? null : user.getDisplayName(),
                user == null ? null : user.getUsername(), request.subject(), request.message(), locale));
    }
}
