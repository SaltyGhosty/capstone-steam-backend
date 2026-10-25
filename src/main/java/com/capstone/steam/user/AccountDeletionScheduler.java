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
 * Anonimizza gli account il cui periodo di cancellazione è scaduto: secondo {@code app.account.deletion-cron} e anche
 * all'avvio (Render free va in sleep e il cron può non scattare mai con il server sveglio). Elimina in ritardo se serve,
 * mai prima della data. Un errore su un account viene solo registrato e si prosegue con gli altri.
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
     * Restituisce quanti account sono stati davvero anonimizzati: uno recuperato tra la query e il lock non
     * conta.
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

    /** Né il cron né l'avvio devono cadere per un errore della query. */
    private void runSafely() {
        try {
            runDue();
        } catch (RuntimeException e) {
            log.warn("No se pudieron procesar las bajas de cuentas: {}", e.getClass().getSimpleName());
        }
    }
}
