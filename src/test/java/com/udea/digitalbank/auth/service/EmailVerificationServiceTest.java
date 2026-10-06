package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.RoleEnum;
import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.Role;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.domain.UserStatus;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.shared.exception.auth.InvalidVerificationCodeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link EmailVerificationService}: confirmación del correo, definición de la
 * contraseña y estado en que queda el usuario según su rol.
 */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String EMAIL = "ana@example.com";
    private static final String CODE = "123456";
    private static final String PASSWORD = "Passw0rd!";

    @Mock
    private UserRepository userRepository;
    @Mock
    private VerificationService verificationService;
    @Mock
    private VerificationDispatcher verificationDispatcher;
    @Mock
    private UserStatusService userStatusService;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    // Role y UserStatus son @Immutable y solo exponen getters: se arman por reflexión solo para el fixture
    private static User buildUser(RoleEnum role, UserStatusEnum status) {
        Role roleEntity = new Role();
        ReflectionTestUtils.setField(roleEntity, "code", role.name());
        UserStatus statusEntity = new UserStatus();
        ReflectionTestUtils.setField(statusEntity, "code", status.name());

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        user.setPasswordHash("hash-aleatorio");
        user.setRole(roleEntity);
        user.setStatus(statusEntity);
        return user;
    }

    private void givenValidCodeFor(User user) {
        when(verificationService.verifyByEmail(EMAIL, PurposeEnum.EMAIL_CONFIRMATION, CODE))
                .thenReturn(user.getId());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    @Nested
    @DisplayName("verifyEmail")
    class VerifyEmail {

        @Test
        @DisplayName("Cliente: guarda la contraseña elegida y queda PENDING_REVIEW")
        void deberiaDejarAlClienteEnRevision() {
            // Arrange
            User user = buildUser(RoleEnum.CUSTOMER, UserStatusEnum.PENDING_VERIFICATION);
            givenValidCodeFor(user);
            when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-elegido");

            // Act
            emailVerificationService.verifyEmail(EMAIL, CODE, PASSWORD, PASSWORD);

            // Assert
            assertThat(user.getPasswordHash()).isEqualTo("hash-elegido");
            assertThat(user.getPasswordChangedAt()).isNotNull();
            verify(userRepository).save(user);
            verify(userStatusService).changeStatus(user.getId(), UserStatusEnum.PENDING_REVIEW);
        }

        @Test
        @DisplayName("Administrador: guarda la contraseña elegida y queda ACTIVE")
        void deberiaActivarDirectamenteAlAdministrador() {
            // Arrange
            User user = buildUser(RoleEnum.ADMIN, UserStatusEnum.PENDING_VERIFICATION);
            givenValidCodeFor(user);
            when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-elegido");

            // Act
            emailVerificationService.verifyEmail(EMAIL, CODE, PASSWORD, PASSWORD);

            // Assert
            assertThat(user.getPasswordHash()).isEqualTo("hash-elegido");
            verify(userStatusService).changeStatus(user.getId(), UserStatusEnum.ACTIVE);
        }

        @Test
        @DisplayName("La contraseña y su confirmación no coinciden: 400 y no se consume el código")
        void deberiaRechazarSiLaConfirmacionNoCoincideSinConsumirElCodigo() {
            // Act & Assert
            assertThatThrownBy(() -> emailVerificationService.verifyEmail(EMAIL, CODE, PASSWORD, "Otra0ContraseñA!"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no coinciden");
            verifyNoInteractions(verificationService, userRepository, passwordEncoder, userStatusService);
        }

        @Test
        @DisplayName("Código inválido o expirado: se propaga y no se toca la contraseña ni el estado")
        void deberiaPropagarElErrorDelCodigo() {
            // Arrange
            when(verificationService.verifyByEmail(EMAIL, PurposeEnum.EMAIL_CONFIRMATION, CODE))
                    .thenThrow(new InvalidVerificationCodeException("Código de verificación inválido o expirado"));

            // Act & Assert
            assertThatThrownBy(() -> emailVerificationService.verifyEmail(EMAIL, CODE, PASSWORD, PASSWORD))
                    .isInstanceOf(InvalidVerificationCodeException.class);
            verifyNoInteractions(userRepository, passwordEncoder, userStatusService);
        }

        @Test
        @DisplayName("Usuario que ya no está PENDING_VERIFICATION: rechaza y no cambia su contraseña")
        void deberiaRechazarSiElUsuarioYaNoEstaPendiente() {
            // Arrange: un reto viejo no debe poder cambiar la contraseña de una cuenta ya habilitada
            User user = buildUser(RoleEnum.CUSTOMER, UserStatusEnum.ACTIVE);
            givenValidCodeFor(user);

            // Act & Assert
            assertThatThrownBy(() -> emailVerificationService.verifyEmail(EMAIL, CODE, PASSWORD, PASSWORD))
                    .isInstanceOf(InvalidVerificationCodeException.class);
            assertThat(user.getPasswordHash()).isEqualTo("hash-aleatorio");
            verifyNoInteractions(passwordEncoder, userStatusService);
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("resendVerification")
    class ResendVerification {

        @Test
        @DisplayName("Usuario pendiente de verificación: emite y envía un código nuevo (con intervalo mínimo)")
        void deberiaReenviarElCodigoAUsuarioPendiente() {
            // Arrange
            User user = buildUser(RoleEnum.CUSTOMER, UserStatusEnum.PENDING_VERIFICATION);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            // Act
            emailVerificationService.resendVerification(EMAIL);

            // Assert
            verify(verificationDispatcher).issueAndSendThrottled(user, PurposeEnum.EMAIL_CONFIRMATION);
        }

        @Test
        @DisplayName("Usuario que ya confirmó su correo: no envía nada")
        void noDeberiaReenviarSiElUsuarioYaNoEstaPendiente() {
            // Arrange
            User user = buildUser(RoleEnum.CUSTOMER, UserStatusEnum.PENDING_REVIEW);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            // Act
            emailVerificationService.resendVerification(EMAIL);

            // Assert
            verifyNoInteractions(verificationDispatcher);
        }

        @Test
        @DisplayName("Correo no registrado: no hace nada observable (no revela qué correos existen)")
        void noDeberiaHacerNadaSiElCorreoNoExiste() {
            // Arrange
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            // Act
            emailVerificationService.resendVerification("noexiste@example.com");

            // Assert
            verifyNoInteractions(verificationDispatcher);
        }
    }
}
