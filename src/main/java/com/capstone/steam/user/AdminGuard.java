package com.capstone.steam.user;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.common.exception.ConflictException;

/** Regla del último administrador: la tienda nunca se queda sin un admin activo. */
@Component
public class AdminGuard {

    private final UserRepository userRepository;

    public AdminGuard(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Bloquea a los administradores que cuentan y falla con 409 si, sin {@code leavingUserId}, no queda ninguno.
     * Va dentro de la transacción del cambio: el bloqueo se mantiene hasta el commit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAnotherAdmin(Long leavingUserId) {
        boolean anotherAdmin = userRepository.lockCountingAdmins().stream()
                .anyMatch(admin -> !admin.getId().equals(leavingUserId));
        if (!anotherAdmin) {
            throw new ConflictException("user.lastAdmin");
        }
    }
}
