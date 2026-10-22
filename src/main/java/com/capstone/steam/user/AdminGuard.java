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
     * <p>
     * Orden de bloqueos: el que llama debe invocarlo ANTES de bloquear cualquier otra fila de usuario en la misma
     * transacción. El guard bloquea a los admins por id ascendente; bloquear antes a un admin objetivo y luego el
     * conjunto podría provocar un interbloqueo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAnotherAdmin(Long leavingUserId) {
        userRepository.lockCountingAdmins(); // solo toma los bloqueos (por id); su resultado no decide nada
        // Se recuenta con una consulta nueva (y por tanto con foto nueva) ya con los bloqueos: en PostgreSQL, tras
        // esperar el bloqueo, la primera consulta no revalida el rol (user_roles) que la otra transacción acaba
        // de quitar, y dos cambios de rol a la vez podrían dejar la tienda sin administradores.
        if (userRepository.countCountingAdminsExcluding(leavingUserId) == 0) {
            throw new ConflictException("user.lastAdmin");
        }
    }
}
