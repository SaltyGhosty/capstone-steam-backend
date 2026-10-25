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
 * Riceve il messaggio del form di assistenza e lo trasforma in un evento email. Non salva nulla nel
 * database: il messaggio esiste solo nella casella del supporto. Senza transazione.
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

    /** @param userId id della sessione, o null se chi scrive è anonimo */
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
