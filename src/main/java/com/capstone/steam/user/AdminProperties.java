package com.capstone.steam.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cuenta de administrador inicial. Si {@code password} está vacío no se crea. */
@ConfigurationProperties("app.admin")
public record AdminProperties(String username, String email, String password) {
}
