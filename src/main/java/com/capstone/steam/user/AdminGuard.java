package com.capstone.steam.user;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.common.exception.ConflictException;

/** Regola dell'ultimo amministratore: lo shop non resta mai senza un admin attivo. */
@Component
public class AdminGuard {

    private final UserRepository userRepository;

    public AdminGuard(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Blocca gli amministratori che contano e fallisce con 409 se, senza {@code leavingUserId}, non ne resta nessuno.
     * Va dentro la transazione della modifica: il lock si mantiene fino al commit.
     * <p>
     * Ordine dei lock: chi lo chiama deve invocarlo PRIMA di bloccare qualsiasi altra riga di utente nella stessa
     * transazione. La guard blocca gli admin per id crescente; bloccare prima un admin bersaglio e poi
     * l'insieme potrebbe causare un deadlock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAnotherAdmin(Long leavingUserId) {
        userRepository.lockCountingAdmins(); // prende solo i lock (per id); il suo risultato non decide nulla
        // Si riconta con una query nuova (quindi con snapshot nuovo) già con i lock: in PostgreSQL, dopo
        // aver atteso il lock, la prima query non rivalida il ruolo (user_roles) che l'altra transazione ha appena
        // tolto, e due cambi di ruolo contemporanei potrebbero lasciare lo shop senza amministratori.
        if (userRepository.countCountingAdminsExcluding(leavingUserId) == 0) {
            throw new ConflictException("user.lastAdmin");
        }
    }
}
