package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link UserCreatedListener}: al confirmarse el registro de un usuario se le envía el
 * código para confirmar su correo y definir su contraseña. Es el mecanismo de activación de HU27 CA05 y el primer
 * paso del flujo de autorregistro de HU01.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU01 / HU27 - UserCreatedListener")
class UserCreatedListenerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private VerificationDispatcher verificationDispatcher;

    private UserCreatedListener listener;

    @BeforeEach
    void setUp() {
        listener = new UserCreatedListener(userRepository, verificationDispatcher);
    }

    @Test
    @DisplayName("HU27 CA05 - Al registrarse el cliente se le envía el código de confirmación de correo")
    void deberiaEnviarElCodigoDeConfirmacion() {
        // Arrange
        User user = AuthFixtures.customer("laura.rojas@example.com", "hash", UserStatusEnum.PENDING_VERIFICATION);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        // Act
        listener.onUserCreated(new UserCreatedEvent(user.getId()));

        // Assert
        verify(verificationDispatcher).issueAndSend(user, PurposeEnum.EMAIL_CONFIRMATION);
    }

    @Test
    @DisplayName("Transversal - Si el usuario ya no existe no se envía nada y el error no se propaga")
    void noDeberiaPropagarSiElUsuarioNoExiste() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatCode(() -> listener.onUserCreated(new UserCreatedEvent(userId))).doesNotThrowAnyException();
        verify(verificationDispatcher, never()).issueAndSend(any(User.class), any(PurposeEnum.class));
    }

    @Test
    @DisplayName("Transversal - Un fallo al enviar el correo no se propaga: el registro ya está confirmado")
    void noDeberiaPropagarUnFalloDelCorreo() {
        // Arrange
        User user = AuthFixtures.customer("laura.rojas@example.com", "hash", UserStatusEnum.PENDING_VERIFICATION);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(verificationDispatcher.issueAndSend(user, PurposeEnum.EMAIL_CONFIRMATION))
                .thenThrow(new IllegalStateException("Resend caído"));

        // Act + Assert
        assertThatCode(() -> listener.onUserCreated(new UserCreatedEvent(user.getId()))).doesNotThrowAnyException();
    }
}
