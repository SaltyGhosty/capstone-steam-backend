package com.capstone.steam.support;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.auth.IpRateLimiter;
import com.capstone.steam.security.AppUserPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Formulario de asistencia. Público: funciona con y sin sesión ({@code user} es null si no la hay). */
@RestController
@RequestMapping("/api/support")
public class SupportController {

    private final SupportService support;
    private final IpRateLimiter ipLimiter;

    public SupportController(SupportService support, IpRateLimiter ipLimiter) {
        this.support = support;
        this.ipLimiter = ipLimiter;
    }

    /** 202 sin cuerpo: el correo sale de forma asíncrona. Límite por IP y 5 por hora y email. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void send(@AuthenticationPrincipal AppUserPrincipal user, @Valid @RequestBody SupportRequest request,
            HttpServletRequest http) {
        ipLimiter.checkAddress(http.getRemoteAddr());
        support.submit(user == null ? null : user.getId(), request);
    }
}
