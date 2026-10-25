package com.capstone.steam.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Account amministratore iniziale. Se {@code password} è vuota non si crea. */
@ConfigurationProperties("app.admin")
public record AdminProperties(String username, String email, String password) {
}
