package com.capstone.steam.user;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Anonimiza las cuentas cuyo plazo de baja ya venció: según {@code app.account.deletion-cron} y también al
 * arrancar (Render free duerme y el cron puede no tocar nunca con el servidor despierto). Elimina tarde si hace
 * falta, nunca antes de la fecha. Un fallo en una cuenta solo se registra y sigue con las demás.
 */
@Component
class AccountDeletionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionScheduler.class);

    private final UserRepository userRepository;
    private final AccountAnonymizer anonymizer;
    private final Clock clock;

    AccountDeletionScheduler(UserRepository userRepository, AccountAnonymizer anonymizer, Clock clock) {
        this.userRepository = userRepository;
        this.anonymizer = anonymizer;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.account.deletion-cron}")
    void scheduled() {
        runSafely();
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        runSafely();
    }

    /**
     * Devuelve cuántas cuentas se anonimizaron de verdad: una que se recuperó entre la consulta y el bloqueo no
     * cuenta.
     */
    int runDue() {
        List<Long> due = userRepository.findIdsDueForDeletion(clock.instant());
        int done = 0;
        for (Long id : due) {
            try {
                if (anonymizer.anonymize(id)) {
                    done++;
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo anonimizar la cuenta {}: {}", id, e.getClass().getSimpleName());
            }
        }
        log.info("Baja de cuentas: {} de {} cuentas vencidas anonimizadas", done, due.size());
        return done;
    }

    /** Ni el cron ni el arranque deben caerse por un fallo de la consulta. */
    private void runSafely() {
        try {
            runDue();
        } catch (RuntimeException e) {
            log.warn("No se pudieron procesar las bajas de cuentas: {}", e.getClass().getSimpleName());
        }
    }
}
