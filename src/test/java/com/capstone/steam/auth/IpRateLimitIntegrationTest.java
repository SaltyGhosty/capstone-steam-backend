package com.capstone.steam.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Límite por IP de las rutas públicas de cuenta: 20 peticiones por minuto y dirección. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.auth.ip-limit.per-minute=20")
class IpRateLimitIntegrationTest {

    @Autowired private MockMvc mvc;

    private ResultActions preview(String ip) throws Exception {
        return mvc.perform(post("/api/auth/verify-email/preview").with(request -> {
            request.setRemoteAddr(ip);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"no-existe\"}"));
    }

    @Test
    void the21stRequestInAMinuteFromTheSameIpIs429ButAnotherIpIsNotAffected() throws Exception {
        for (int i = 0; i < 20; i++) {
            preview("203.0.113.7").andExpect(status().isBadRequest()); // enlace inexistente, pero pasa el límite
        }

        preview("203.0.113.7").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("auth.tooManyRequests"))
                .andExpect(jsonPath("$.detail").value("Demasiadas peticiones. Espera un momento."));
        preview("203.0.113.8").andExpect(status().isBadRequest());
    }

    @Test
    void theLimitCoversRegisterResendAndVerify() throws Exception {
        String ip = "198.51.100.9";
        for (int i = 0; i < 20; i++) {
            mvc.perform(post("/api/auth/resend-verification").with(r -> {
                r.setRemoteAddr(ip);
                return r;
            }).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nadie@test.com\"}"));
        }

        mvc.perform(post("/api/auth/register").with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ana\",\"email\":\"ana@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/auth/verify-email").with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
