package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.shared.exception.auth.InvalidVerificationCodeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link PasswordResetService} (HU05 Recuperar contraseña).
 * Esta clase no tenía ninguna prueba: solo se conocía indirectamente a través del mock en otros
 * tests, así que ninguna de las 19 CA de HU05 estaba realmente verificada.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private VerificationService verificationService;
    @Mock
    private VerificationDispatcher verificationDispatcher;
    @Mock
    private SessionService sessionService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Nested
    @DisplayName("requestPasswordReset - CA01/CA02 (mensaje genérico, no revela si el correo existe)")
    class RequestPasswordReset {

        @Test
        @DisplayName("CA02 - Correo registrado: emite y envía el código de recuperación")
        void deberiaEmitirYEnviarElCodigoCuandoElCorreoEstaRegistrado() {
            // Arrange
            User user = new User();
            user.setId(1L);
            user.setEmail("cliente@example.com");
            when(userRepository.findByEmail("cliente@example.com")).thenReturn(Optional.of(user));

            // Act
            passwordResetService.requestPasswordReset("cliente@example.com");

            // Assert
            verify(verificationDispatcher).issueAndSendThrottled(user, PurposeEnum.PASSWORD_RESET);
        }

        @Test
        @DisplayName("CA01 - Correo no registrado: no hace nada observable (mismo comportamiento externo)")
        void noDeberiaHacerNadaCuandoElCorreoNoEstaRegistrado() {
            // Arrange
            when(userRepository.findByEmail("noexiste@example.com")).thenReturn(Optional.empty());

            // Act
            passwordResetService.requestPasswordReset("noexiste@example.com");

            // Assert: no se emite ningún código y tampoco se lanza una excepción que delate el caso
            verifyNoInteractions(verificationDispatcher);
        }
    }

    @Nested
    @DisplayName("resetPassword - CA12 (éxito) y propagación de errores del código")
    class ResetPassword {

        @Test
        @DisplayName("CA12/CA19 - Código válido y contraseña válida: actualiza la contraseña y revoca la sesión")
        void deberiaActualizarLaContraseñaYRevocarLaSesionCuandoElCodigoEsValido() {
            // Arrange
            Long userId = 10L;
            User user = new User();
            user.setId(userId);
            user.setFailedAttempts(2); // intentos previos de login, deben resetearse también aquí

            when(verificationService.verifyByEmail("cliente@example.com", PurposeEnum.PASSWORD_RESET, "654321"))
                    .thenReturn(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("NuevaClave1$")).thenReturn("hash-nuevo");

            // Act
            passwordResetService.resetPassword("cliente@example.com", "654321", "NuevaClave1$");

            // Assert
            assertThat(user.getPasswordHash()).isEqualTo("hash-nuevo");
            assertThat(user.getPasswordChangedAt()).isNotNull();
            assertThat(user.getFailedAttempts()).isZero();
            verify(userRepository).save(user);
            verify(sessionService).revoke(userId); // CA19: no continúa con una sesión antigua
        }

        @Test
        @DisplayName("Código inválido o expirado: se propaga la excepción y no se toca la contraseña")
        void deberiaPropagarLaExcepcionYNoModificarNadaCuandoElCodigoEsInvalido() {
            // Arrange
            when(verificationService.verifyByEmail("cliente@example.com", PurposeEnum.PASSWORD_RESET, "000000"))
                    .thenThrow(new InvalidVerificationCodeException("Código de verificación inválido o expirado"));

            // Act & Assert
            assertThatThrownBy(() -> passwordResetService.resetPassword("cliente@example.com", "000000", "NuevaClave1$"))
                    .isInstanceOf(InvalidVerificationCodeException.class);
            verifyNoInteractions(userRepository, passwordEncoder, sessionService);
        }
    }
}