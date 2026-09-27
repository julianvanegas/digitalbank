package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.shared.utils.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link VerificationDispatcher}. Se mockean VerificationService y
 * EmailService: aquí solo se prueba que se arme y envíe el mensaje correcto según el propósito,
 * y que el flujo "throttled" no envíe nada si VerificationService decide no emitir un código nuevo.
 */
@ExtendWith(MockitoExtension.class)
class VerificationDispatcherTest {

    @Mock
    private VerificationService verificationService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private VerificationDispatcher dispatcher;

    private static User buildUser(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    @Nested
    @DisplayName("issueAndSend - HU03 CA01 (emitir y enviar el código de verificación)")
    class IssueAndSend {

        @Test
        @DisplayName("Emite el reto y envía un correo con el asunto correspondiente a LOGIN")
        void deberiaEnviarElCorreoDeLoginConElAsuntoCorrecto() {
            // Arrange
            User user = buildUser(1L, "cliente@example.com");
            IssuedChallenge challenge = new IssuedChallenge(UUID.randomUUID(), "123456", 5);
            when(verificationService.issue(user, PurposeEnum.LOGIN)).thenReturn(challenge);

            // Act
            IssuedChallenge result = dispatcher.issueAndSend(user, PurposeEnum.LOGIN);

            // Assert
            assertThat(result).isEqualTo(challenge);
            verify(emailService).send(
                    eq("cliente@example.com"),
                    eq("Tu código de verificación"),
                    contains("123456"));
        }

        @Test
        @DisplayName("Usa el asunto de recuperación de contraseña para PASSWORD_RESET")
        void deberiaUsarElAsuntoDeRecuperacionParaPasswordReset() {
            // Arrange
            User user = buildUser(2L, "otro@example.com");
            IssuedChallenge challenge = new IssuedChallenge(UUID.randomUUID(), "654321", 5);
            when(verificationService.issue(user, PurposeEnum.PASSWORD_RESET)).thenReturn(challenge);

            // Act
            dispatcher.issueAndSend(user, PurposeEnum.PASSWORD_RESET);

            // Assert
            verify(emailService).send(eq("otro@example.com"), eq("Recuperación de contraseña"), anyString());
        }

        @Test
        @DisplayName("Usa el asunto de confirmación de correo para EMAIL_CONFIRMATION")
        void deberiaUsarElAsuntoDeConfirmacionParaEmailConfirmation() {
            // Arrange
            User user = buildUser(3L, "nuevo@example.com");
            IssuedChallenge challenge = new IssuedChallenge(UUID.randomUUID(), "111111", 30);
            when(verificationService.issue(user, PurposeEnum.EMAIL_CONFIRMATION)).thenReturn(challenge);

            // Act
            dispatcher.issueAndSend(user, PurposeEnum.EMAIL_CONFIRMATION);

            // Assert
            verify(emailService).send(eq("nuevo@example.com"), eq("Confirma tu correo electrónico"), anyString());
        }
    }

    @Nested
    @DisplayName("issueAndSendThrottled - evita saturar la bandeja en flujos públicos")
    class IssueAndSendThrottled {

        @Test
        @DisplayName("Si VerificationService emite un reto nuevo, se envía el correo")
        void deberiaEnviarElCorreoCuandoSeEmiteUnRetoNuevo() {
            // Arrange
            User user = buildUser(4L, "cliente@example.com");
            IssuedChallenge challenge = new IssuedChallenge(UUID.randomUUID(), "999999", 5);
            when(verificationService.issueThrottled(user, PurposeEnum.PASSWORD_RESET))
                    .thenReturn(Optional.of(challenge));

            // Act
            dispatcher.issueAndSendThrottled(user, PurposeEnum.PASSWORD_RESET);

            // Assert
            verify(emailService).send(eq("cliente@example.com"), anyString(), contains("999999"));
        }

        @Test
        @DisplayName("Si el último código fue muy reciente, no se envía ningún correo")
        void noDeberiaEnviarNadaCuandoElUltimoCodigoFueMuyReciente() {
            // Arrange
            User user = buildUser(4L, "cliente@example.com");
            when(verificationService.issueThrottled(user, PurposeEnum.PASSWORD_RESET))
                    .thenReturn(Optional.empty());

            // Act
            dispatcher.issueAndSendThrottled(user, PurposeEnum.PASSWORD_RESET);

            // Assert
            verifyNoInteractions(emailService);
        }
    }
}