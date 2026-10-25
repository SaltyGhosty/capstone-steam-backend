package com.capstone.steam.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger UI su /swagger-ui.html. Il pulsante "Authorize" accetta l'accessToken di /api/auth/login
 * (senza il prefisso "Bearer"); gli endpoint pubblici funzionano uguale senza token.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";

    @Bean
    OpenAPI capstoneSteamOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Capstone Steam API")
                        .version("v1")
                        .description("Tienda de videojuegos: catálogo, carrito, checkout con pago simulado, "
                                + "biblioteca, wishlist, reseñas y paneles de developer y admin."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
