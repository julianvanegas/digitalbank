package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.domain.UserStatus;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.shared.exception.auth.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link UserStatusService}.
 * Es la clase que de verdad implementa el bloqueo tras 3 intentos fallidos (HU02 CA05) y los
 * cambios de estado que revocan la sesión (HU04): antes solo se mockeaba desde AuthServiceTest,
 * así que esa regla nunca se había verificado de forma directa.
 */
@ExtendWith(MockitoExtension.class)
class UserStatusServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private Catalogs catalogs;

    @InjectMocks
    private UserStatusService userStatusService;

    private static User buildUser(Long id, UserStatusEnum statusEnum, int failedAttempts) {
        User user = new User();
        user.setId(id);
        user.setStatus(buildStatus(statusEnum));
        user.setFailedAttempts(failedAttempts);
        return user;
    }

    // UserStatus es @Immutable y solo expone getters: se arma por reflexión únicamente para el fixture
    private static UserStatus buildStatus(UserStatusEnum statusEnum) {
        UserStatus status = new UserStatus();
        setField(status, "code", statusEnum.name());
        return status;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("registerFailedAttempt - HU02 CA05 (bloqueo tras 3 intentos fallidos)")
    class RegisterFailedAttempt {

        @Test
        @DisplayName("Primer intento fallido: suma uno y no bloquea")
        void deberiaSumarUnIntentoSinBloquearEnElPrimerFallo() {
            // Arrange
            User user = buildUser(1L, UserStatusEnum.ACTIVE, 0);

            // Act
            userStatusService.registerFailedAttempt(user);

            // Assert
            assertThat(user.getFailedAttempts()).isEqualTo(1);
            assertThat(user.getStatus().getCode()).isEqualTo("ACTIVE"); // sigue activo
            verify(userRepository, times(1)).save(user); // solo el save final, no pasa por applyStatus
            verifyNoInteractions(sessionService, catalogs);
        }

        @Test
        @DisplayName("Segundo intento fallido: suma uno y tampoco bloquea todavía")
        void deberiaSumarUnIntentoSinBloquearEnElSegundoFallo() {
            // Arrange: ya viene con 1 intento fallido previo
            User user = buildUser(1L, UserStatusEnum.ACTIVE, 1);

            // Act
            userStatusService.registerFailedAttempt(user);

            // Assert
            assertThat(user.getFailedAttempts()).isEqualTo(2);
            verify(userRepository, times(1)).save(user);
            verifyNoInteractions(sessionService, catalogs);
        }

        @Test
        @DisplayName("Tercer intento fallido: bloquea al usuario y revoca su sesión")
        void deberiaBloquearAlUsuarioEnElTercerIntentoFallido() {
            // Arrange: ya viene con 2 intentos fallidos previos, este es el tercero
            User user = buildUser(1L, UserStatusEnum.ACTIVE, 2);
            when(catalogs.status(UserStatusEnum.BLOCKED)).thenReturn(buildStatus(UserStatusEnum.BLOCKED));

            // Act
            userStatusService.registerFailedAttempt(user);

            // Assert
            assertThat(user.getFailedAttempts()).isEqualTo(3);
            assertThat(user.getStatus().getCode()).isEqualTo("BLOCKED");
            verify(sessionService).revoke(1L); // un usuario bloqueado no debe conservar sesión activa
            // Se guarda dos veces: una dentro de applyStatus() y otra al final de registerFailedAttempt().
            // Es un detalle de implementación (podría optimizarse a un solo save), pero así se comporta hoy.
            verify(userRepository, times(2)).save(user);
        }
    }

    @Nested
    @DisplayName("changeStatus - usado por HU04 (invalidar sesión al cambiar de estado)")
    class ChangeStatus {

        @Test
        @DisplayName("Cambiar a ACTIVE resetea los intentos fallidos y no revoca sesión")
        void deberiaResetearIntentosFallidosAlActivarYNoRevocarSesion() {
            // Arrange
            User user = buildUser(2L, UserStatusEnum.BLOCKED, 3);
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(catalogs.status(UserStatusEnum.ACTIVE)).thenReturn(buildStatus(UserStatusEnum.ACTIVE));

            // Act
            userStatusService.changeStatus(2L, UserStatusEnum.ACTIVE);

            // Assert
            assertThat(user.getStatus().getCode()).isEqualTo("ACTIVE");
            assertThat(user.getFailedAttempts()).isZero();
            verifyNoInteractions(sessionService);
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("Cambiar a un estado distinto de ACTIVE revoca la sesión del usuario")
        void deberiaRevocarLaSesionAlCambiarAUnEstadoNoActivo() {
            // Arrange
            User user = buildUser(2L, UserStatusEnum.ACTIVE, 0);
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(catalogs.status(UserStatusEnum.INACTIVE)).thenReturn(buildStatus(UserStatusEnum.INACTIVE));

            // Act
            userStatusService.changeStatus(2L, UserStatusEnum.INACTIVE);

            // Assert
            assertThat(user.getStatus().getCode()).isEqualTo("INACTIVE");
            verify(sessionService).revoke(2L);
        }

        @Test
        @DisplayName("Usuario inexistente: rechaza el cambio de estado")
        void deberiaRechazarElCambioCuandoElUsuarioNoExiste() {
            // Arrange
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userStatusService.changeStatus(99L, UserStatusEnum.ACTIVE))
                    .isInstanceOf(UserNotFoundException.class);
            verifyNoInteractions(sessionService, catalogs);
        }
    }

    @Nested
    @DisplayName("isActive - consultado en cada petición autenticada")
    class IsActive {

        @Test
        @DisplayName("Devuelve true cuando el usuario existe y está ACTIVE")
        void deberiaRetornarTrueCuandoElUsuarioEstaActivo() {
            // Arrange
            User user = buildUser(3L, UserStatusEnum.ACTIVE, 0);
            when(userRepository.findById(3L)).thenReturn(Optional.of(user));

            // Act & Assert
            assertThat(userStatusService.isActive(3L)).isTrue();
        }

        @Test
        @DisplayName("Devuelve false cuando el usuario existe pero no está ACTIVE")
        void deberiaRetornarFalseCuandoElUsuarioNoEstaActivo() {
            // Arrange
            User user = buildUser(3L, UserStatusEnum.BLOCKED, 3);
            when(userRepository.findById(3L)).thenReturn(Optional.of(user));

            // Act & Assert
            assertThat(userStatusService.isActive(3L)).isFalse();
        }

        @Test
        @DisplayName("Devuelve false cuando el usuario no existe")
        void deberiaRetornarFalseCuandoElUsuarioNoExiste() {
            // Arrange
            when(userRepository.findById(404L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThat(userStatusService.isActive(404L)).isFalse();
        }
    }
}