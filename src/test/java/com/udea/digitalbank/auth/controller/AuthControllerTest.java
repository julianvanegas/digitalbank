package com.udea.digitalbank.auth.controller;

import com.udea.digitalbank.auth.dto.AuthResponse;
import com.udea.digitalbank.auth.dto.EmailRequest;
import com.udea.digitalbank.auth.dto.LoginRequest;
import com.udea.digitalbank.auth.dto.LoginResponse;
import com.udea.digitalbank.auth.dto.PasswordResetRequest;
import com.udea.digitalbank.auth.dto.VerifyEmailRequest;
import com.udea.digitalbank.auth.dto.VerifyTwoFactorRequest;
import com.udea.digitalbank.auth.service.AuthService;
import com.udea.digitalbank.auth.service.EmailVerificationService;
import com.udea.digitalbank.auth.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) para AuthController. Se instancia directamente, sin MockMvc ni
 * contexto de Spring: un controller aquí es solo un "pasamanos" hacia los services, así que
 * basta con verificar el código HTTP devuelto y que delega los parámetros correctos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private Authentication authentication;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, emailVerificationService, passwordResetService);
    }

    @Test
    @DisplayName("verifyEmail - HU01 (confirmación de correo): delega y responde 204")
    void verifyEmail_deberiaDelegarYResponder204() {
        // Arrange
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setEmail("ana@example.com");
        request.setCode("123456");

        // Act
        ResponseEntity<Void> response = controller.verifyEmail(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(emailVerificationService).verifyEmail("ana@example.com", "123456");
    }

    @Test
    @DisplayName("resendVerification - delega y responde 204")
    void resendVerification_deberiaDelegarYResponder204() {
        // Arrange
        EmailRequest request = new EmailRequest();
        request.setEmail("ana@example.com");

        // Act
        ResponseEntity<Void> response = controller.resendVerification(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(emailVerificationService).resendVerification("ana@example.com");
    }

    @Test
    @DisplayName("login - HU02: responde 200 con el resultado de AuthService, sin emitir el JWT todavía")
    void login_deberiaResponder200ConResultadoDelService() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail("ana@example.com");
        request.setPassword("Passw0rd!");
        UUID challengeId = UUID.randomUUID();
        LoginResponse expected = new LoginResponse(true, challengeId);
        when(authService.login(request)).thenReturn(expected);

        // Act
        ResponseEntity<LoginResponse> response = controller.login(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("verifyTwoFactor - HU03: delega challengeId y code, responde 200 con el token")
    void verifyTwoFactor_deberiaDelegarYResponder200() {
        // Arrange
        UUID challengeId = UUID.randomUUID();
        VerifyTwoFactorRequest request = new VerifyTwoFactorRequest();
        request.setChallengeId(challengeId);
        request.setCode("654321");
        AuthResponse expected = new AuthResponse("jwt-token");
        when(authService.verifyTwoFactor(challengeId, "654321")).thenReturn(expected);

        // Act
        ResponseEntity<AuthResponse> response = controller.verifyTwoFactor(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getToken()).isEqualTo("jwt-token");
    }

    @Test
    @DisplayName("logout - HU04: obtiene el userId del principal autenticado y responde 204")
    void logout_deberiaUsarPrincipalYResponder204() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(42L);

        // Act
        ResponseEntity<Void> response = controller.logout(authentication);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService).logout(42L);
    }

    @Test
    @DisplayName("recoverPassword - HU05: delega el email y responde 204 (mensaje siempre genérico)")
    void recoverPassword_deberiaDelegarYResponder204() {
        // Arrange
        EmailRequest request = new EmailRequest();
        request.setEmail("ana@example.com");

        // Act
        ResponseEntity<Void> response = controller.recoverPassword(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(passwordResetService).requestPasswordReset("ana@example.com");
    }

    @Test
    @DisplayName("resetPassword - HU05: delega email, código y nueva contraseña, responde 204")
    void resetPassword_deberiaDelegarYResponder204() {
        // Arrange
        PasswordResetRequest request = new PasswordResetRequest();
        request.setEmail("ana@example.com");
        request.setCode("123456");
        request.setPassword("NuevaPass1!");

        // Act
        ResponseEntity<Void> response = controller.resetPassword(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(passwordResetService).resetPassword("ana@example.com", "123456", "NuevaPass1!");
    }
}