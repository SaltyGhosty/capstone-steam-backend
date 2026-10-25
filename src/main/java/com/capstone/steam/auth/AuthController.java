package com.capstone.steam.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.auth.dto.AuthResponse;
import com.capstone.steam.auth.dto.LoginRequest;
import com.capstone.steam.auth.dto.RecoverAccountRequest;
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
        ipLimiter.checkAddress(http.getRemoteAddr());
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Annulla la cancellazione programmata da parte del proprietario e avvia la sessione. Conta nel limite per IP. */
    @PostMapping("/recover-account")
    public AuthResponse recoverAccount(@Valid @RequestBody RecoverAccountRequest request, HttpServletRequest http) {
        ipLimiter.checkAddress(http.getRemoteAddr());
        return authService.recoverAccount(request.login(), request.password());
    }

    /** Riscatta il link dell'email: verifica l'email e avvia la sessione. */
    @PostMapping("/verify-email")
    public AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request, HttpServletRequest http) {
        ipLimiter.checkAddress(http.getRemoteAddr());
        return authService.verifyEmail(request.token(), request.password());
    }

    /** A quale account appartiene il link (se è ancora valido), perché la pagina chieda la sua password. Idempotente. */
    @PostMapping("/verify-email/preview")
    public VerificationPreview previewVerification(@Valid @RequestBody VerificationPreviewRequest request,
            HttpServletRequest http) {
        ipLimiter.checkAddress(http.getRemoteAddr());
        return new VerificationPreview(verificationService.preview(request.token()));
    }

    /** Sempre 204: non rivela se l'email esiste né se è già verificata. Massimo 3 all'ora per email. */
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request, HttpServletRequest http) {
        ipLimiter.checkAddress(http.getRemoteAddr());
        verificationService.resend(request.email());
    }

    /** Scambia un refresh token con un nuovo access token e un nuovo refresh token. */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** Chiude la sessione del refresh token indicato; idempotente. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }
}
