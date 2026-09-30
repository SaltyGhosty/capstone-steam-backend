package com.capstone.steam.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger UI en /swagger-ui.html. El botón "Authorize" acepta el accessToken de /api/auth/login
 * (sin el prefijo "Bearer"); los endpoints públicos funcionan igual sin token.
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
