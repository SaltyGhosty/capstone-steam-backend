package com.capstone.steam.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.auth.dto.AuthResponse;
import com.capstone.steam.auth.dto.LoginRequest;
import com.capstone.steam.auth.dto.RefreshRequest;
import com.capstone.steam.auth.dto.RegisterRequest;
import com.capstone.steam.auth.dto.RegistrationResult;
import com.capstone.steam.auth.dto.ResendVerificationRequest;
import com.capstone.steam.auth.dto.VerificationPreview;
import com.capstone.steam.auth.dto.VerificationPreviewRequest;
import com.capstone.steam.auth.dto.VerifyEmailRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService verificationService;
    private final IpRateLimiter ipLimiter;

    public AuthController(AuthService authService, EmailVerificationService verificationService,
            IpRateLimiter ipLimiter) {
        this.authService = authService;
        this.verificationService = verificationService;
        this.ipLimiter = ipLimiter;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResult register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        ipLimiter.check(http.getRemoteAddr());
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Canjea el enlace del correo: verifica el email e inicia la sesión. */
    @PostMapping("/verify-email")
    public AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request, HttpServletRequest http) {
        ipLimiter.check(http.getRemoteAddr());
        return authService.verifyEmail(request.token(), request.password());
    }

    /** De qué cuenta es el enlace (si sigue vigente), para que la página pida su contraseña. Idempotente. */
    @PostMapping("/verify-email/preview")
    public VerificationPreview previewVerification(@Valid @RequestBody VerificationPreviewRequest request,
            HttpServletRequest http) {
        ipLimiter.check(http.getRemoteAddr());
        return new VerificationPreview(verificationService.preview(request.token()));
    }

    /** Siempre 204: no revela si el email existe ni si ya está verificado. Máximo 3 por hora y email. */
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request, HttpServletRequest http) {
        ipLimiter.check(http.getRemoteAddr());
        verificationService.resend(request.email());
    }

    /** Canjea un refresh token por un acceso y un refresh nuevos. */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** Cierra la sesión del refresh token dado; idempotente. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }
}
