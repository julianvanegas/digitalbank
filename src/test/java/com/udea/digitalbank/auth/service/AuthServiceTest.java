package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.domain.UserStatus;
import com.udea.digitalbank.auth.dto.AuthResponse;
import com.udea.digitalbank.auth.dto.LoginRequest;
import com.udea.digitalbank.auth.dto.LoginResponse;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.shared.exception.auth.InvalidCredentialsException;
import com.udea.digitalbank.shared.exception.auth.InvalidVerificationCodeException;
import com.udea.digitalbank.shared.exception.auth.UserNotEnabledException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link AuthService}.
 * Se mockean UserRepository, SessionService, PasswordEncoder, VerificationService,
 * VerificationDispatcher y UserStatusService: aquí solo se prueba la orquestación
 * (qué decide AuthService ante cada combinación de credenciales/estado), no el hash real
 * de contraseñas ni la persistencia.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private VerificationService verificationService;
    @Mock
    private VerificationDispatcher verificationDispatcher;
    @Mock
    private UserStatusService userStatusService;

    @InjectMocks
    private AuthService authService;

    private static User buildUser(String email, String passwordHash, UserStatusEnum statusEnum) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setStatus(buildStatus(statusEnum));
        user.setFailedAttempts(0);
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

    private static LoginRequest buildLoginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    @Nested
    @DisplayName("login - HU02 Iniciar sesión")
    class Login {

        @Test
        @DisplayName("CA01 - Credenciales válidas: resetea intentos fallidos y envía el reto 2FA")
        void deberiaAceptarCredencialesValidasYContinuarConEl2FA() {
            // Arrange
            User user = buildUser("cliente@example.com", "hash", UserStatusEnum.ACTIVE);
            user.setFailedAttempts(2); // intentos previos que deben resetearse al acertar
            LoginRequest request = buildLoginRequest("cliente@example.com", "Abcdef1$");
            UUID challengeId = UUID.randomUUID();

            when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(request.getPassword(), user.getPasswordHash())).thenReturn(true);
            when(verificationDispatcher.issueAndSend(user, PurposeEnum.LOGIN))
                    .thenReturn(new IssuedChallenge(challengeId, "123456", 5));

            // Act
            LoginResponse response = authService.login(request);

            // Assert
            assertThat(response.isRequiresTwoFactor()).isTrue();
            assertThat(response.getChallengeId()).isEqualTo(challengeId);
            assertThat(user.getFailedAttempts()).isZero();
            verify(userRepository).save(user);
            verify(userStatusService, never()).registerFailedAttempt(any());
            verify(verificationDispatcher).issueAndSend(user, PurposeEnum.LOGIN);
        }

        @Test
        @DisplayName("CA02 - Contraseña incorrecta con usuario activo: registra el intento fallido")
        void deberiaRechazarYRegistrarIntentoCuandoLaContraseñaEsIncorrectaYElUsuarioEstaActivo() {
            // Arrange
            User user = buildUser("cliente@example.com", "hash", UserStatusEnum.ACTIVE);
            LoginRequest request = buildLoginRequest("cliente@example.com", "incorrecta");

            when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(request.getPassword(), user.getPasswordHash())).thenReturn(false);

            // Act
            Throwable thrown = catchThrowableFrom(() -> authService.login(request));

            // Assert
            assertThat(thrown).isInstanceOf(InvalidCredentialsException.class);
            verify(userStatusService).registerFailedAttempt(user);
            verify(verificationDispatcher, never()).issueAndSend(any(), any());
        }

        @Test
        @DisplayName("No registra intento fallido si el usuario ya no está ACTIVE (p. ej. ya bloqueado)")
        void noDeberiaRegistrarIntentoCuandoElUsuarioNoEstaActivo() {
            // Arrange: un usuario ya bloqueado no debe seguir acumulando intentos
            User user = buildUser("cliente@example.com", "hash", UserStatusEnum.BLOCKED);
            LoginRequest request = buildLoginRequest("cliente@example.com", "incorrecta");

            when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(request.getPassword(), user.getPasswordHash())).thenReturn(false);

            // Act
            Throwable thrown = catchThrowableFrom(() -> authService.login(request));

            // Assert
            assertThat(thrown).isInstanceOf(InvalidCredentialsException.class);
            verify(userStatusService, never()).registerFailedAttempt(any());
        }

        @Test
        @DisplayName("CA03 - Correo no registrado: mismo mensaje genérico que credenciales incorrectas")
        void deberiaRechazarConMensajeGenericoCuandoElCorreoNoExiste() {
            // Arrange
            LoginRequest request = buildLoginRequest("noexiste@example.com", "cualquiera");
            when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

            // Act
            Throwable thrown = catchThrowableFrom(() -> authService.login(request));

            // Assert
            assertThat(thrown).isInstanceOf(InvalidCredentialsException.class);
            verifyNoInteractions(passwordEncoder, userStatusService, verificationDispatcher);
        }

        @Test
        @DisplayName("CA06 - Usuario bloqueado con credenciales correctas: acceso rechazado")
        void deberiaRechazarInicioDeSesionCuandoElUsuarioEstaBloqueado() {
            // Arrange
            User user = buildUser("cliente@example.com", "hash", UserStatusEnum.BLOCKED);
            LoginRequest request = buildLoginRequest("cliente@example.com", "Abcdef1$");

            when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(request.getPassword(), user.getPasswordHash())).thenReturn(true);

            // Act
            Throwable thrown = catchThrowableFrom(() -> authService.login(request));

            // Assert
            assertThat(thrown).isInstanceOf(UserNotEnabledException.class);
            verifyNoInteractions(verificationDispatcher);
        }

        @Test
        @DisplayName("CA08 - Usuario con correo aún no confirmado: acceso rechazado")
        void deberiaRechazarInicioDeSesionCuandoElUsuarioNoEstaHabilitado() {
            // Arrange
            User user = buildUser("cliente@example.com", "hash", UserStatusEnum.PENDING_VERIFICATION);
            LoginRequest request = buildLoginRequest("cliente@example.com", "Abcdef1$");

            when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(request.getPassword(), user.getPasswordHash())).thenReturn(true);

            // Act
            Throwable thrown = catchThrowableFrom(() -> authService.login(request));

            // Assert
            assertThat(thrown).isInstanceOf(UserNotEnabledException.class);
            verifyNoInteractions(verificationDispatcher);
        }

        @Test
        @DisplayName("Estado INACTIVE tampoco permite iniciar sesión (rechazo por defecto)")
        void deberiaRechazarInicioDeSesionParaUsuarioInactivo() {
            // Arrange
            User user = buildUser("cliente@example.com", "hash", UserStatusEnum.INACTIVE);
            LoginRequest request = buildLoginRequest("cliente@example.com", "Abcdef1$");

            when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(request.getPassword(), user.getPasswordHash())).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(UserNotEnabledException.class);
        }

        private Throwable catchThrowableFrom(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
            return org.assertj.core.api.Assertions.catchThrowable(callable);
        }
    }

    @Nested
    @DisplayName("verifyTwoFactor - HU03 Verificar identidad mediante 2FA")
    class VerifyTwoFactor {

        @Test
        @DisplayName("CA02/CA08 - Código válido: crea la sesión y devuelve el token")
        void deberiaCompletarLaVerificacionYDevolverElToken() {
            // Arrange
            UUID challengeId = UUID.randomUUID();
            User user = buildUser("cliente@example.com", "hash", UserStatusEnum.ACTIVE);

            when(verificationService.verifyById(challengeId, PurposeEnum.LOGIN, "123456"))
                    .thenReturn(user.getId());
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(sessionService.start(user)).thenReturn("jwt-token");

            // Act
            AuthResponse response = authService.verifyTwoFactor(challengeId, "123456");

            // Assert
            assertThat(response.getToken()).isEqualTo("jwt-token");
            assertThat(user.getLastLoginAt()).isNotNull();
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getLastLoginAt()).isNotNull();
        }

        @Test
        @DisplayName("CA04 - Código incorrecto: la excepción de VerificationService se propaga")
        void deberiaPropagarLaExcepcionCuandoElCodigoEsInvalido() {
            // Arrange
            UUID challengeId = UUID.randomUUID();
            when(verificationService.verifyById(challengeId, PurposeEnum.LOGIN, "000000"))
                    .thenThrow(new InvalidVerificationCodeException("Código de verificación inválido o expirado"));

            // Act & Assert
            assertThatThrownBy(() -> authService.verifyTwoFactor(challengeId, "000000"))
                    .isInstanceOf(InvalidVerificationCodeException.class);
            verifyNoInteractions(sessionService);
        }

        @Test
        @DisplayName("CA13 - El usuario se bloqueó entre el login y la verificación: acceso rechazado")
        void deberiaRechazarCuandoElUsuarioYaNoEstaActivoAlVerificar() {
            // Arrange
            UUID challengeId = UUID.randomUUID();
            User user = buildUser("cliente@example.com", "hash", UserStatusEnum.BLOCKED);

            when(verificationService.verifyById(challengeId, PurposeEnum.LOGIN, "123456"))
                    .thenReturn(user.getId());
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            // Act & Assert
            assertThatThrownBy(() -> authService.verifyTwoFactor(challengeId, "123456"))
                    .isInstanceOf(UserNotEnabledException.class);
            verifyNoInteractions(sessionService);
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("logout - HU04 Gestionar sesión")
    class Logout {

        @Test
        @DisplayName("CA02 - Cerrar sesión revoca la sesión del usuario")
        void deberiaRevocarLaSesionDelUsuario() {
            // Arrange
            Long userId = 42L;

            // Act
            authService.logout(userId);

            // Assert
            verify(sessionService).revoke(userId);
        }
    }
}