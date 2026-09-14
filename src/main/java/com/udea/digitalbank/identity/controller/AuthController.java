package com.udea.digitalbank.identity.controller;

import com.udea.digitalbank.identity.dto.AuthResponse;
import com.udea.digitalbank.identity.dto.CustomerResponse;
import com.udea.digitalbank.identity.dto.LoginRequest;
import com.udea.digitalbank.identity.dto.LoginResponse;
import com.udea.digitalbank.identity.dto.RegistrationRequest;
import com.udea.digitalbank.identity.dto.PasswordResetRequest;
import com.udea.digitalbank.identity.service.AuthService;
import com.udea.digitalbank.identity.service.CustomerService;
import com.udea.digitalbank.identity.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CustomerService customerService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService,
                          CustomerService customerService,
                          PasswordResetService passwordResetService) {
        this.authService = authService;
        this.customerService = customerService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody RegistrationRequest request) {
        CustomerResponse response = customerService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // requiresTwoFactor siempre viene en true si las credenciales son correctas:
        // el JWT todavía no se emite en este paso, ver AuthService.login
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-2fa")
    public ResponseEntity<AuthResponse> verifyTwoFactor(@RequestParam Long customerId,
                                                        @RequestParam String code) {
        AuthResponse response = authService.verifyTwoFactor(customerId, code);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        // El JwtAuthenticationFilter guarda el customerId como principal (ver JwtAuthenticationFilter)
        Long customerId = (Long) authentication.getPrincipal();
        authService.logout(customerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recover-password")
    public ResponseEntity<Void> recoverPassword(@RequestParam String email) {
        passwordResetService.requestReset(email);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getPassword());
        return ResponseEntity.noContent().build();
    }
}