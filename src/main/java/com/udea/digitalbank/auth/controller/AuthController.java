package com.udea.digitalbank.auth.controller;

import com.udea.digitalbank.auth.dto.AuthResponse;
import com.udea.digitalbank.auth.dto.LoginRequest;
import com.udea.digitalbank.auth.dto.LoginResponse;
import com.udea.digitalbank.auth.dto.PasswordResetRequest;
import com.udea.digitalbank.auth.dto.VerifyEmailRequest;
import com.udea.digitalbank.auth.dto.VerifyTwoFactorRequest;
import com.udea.digitalbank.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// No hay endpoint de registro: las cuentas se crean desde el módulo de la persona (AuthFacade.createAccount)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.getEmail(), request.getCode());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@RequestParam String email) {
        authService.resendVerification(email);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // requiresTwoFactor siempre viene en true si las credenciales son correctas:
        // el JWT todavía no se emite en este paso, ver AuthService.login
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/verify-2fa")
    public ResponseEntity<AuthResponse> verifyTwoFactor(@Valid @RequestBody VerifyTwoFactorRequest request) {
        return ResponseEntity.ok(authService.verifyTwoFactor(request.getChallengeId(), request.getCode()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        // El JwtAuthenticationFilter guarda el id de la cuenta como principal
        authService.logout((Long) authentication.getPrincipal());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recover-password")
    public ResponseEntity<Void> recoverPassword(@RequestParam String email) {
        authService.requestPasswordReset(email);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request.getEmail(), request.getCode(), request.getPassword());
        return ResponseEntity.noContent().build();
    }
}
