package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.dto.LoginRequest;
import com.udea.digitalbank.auth.dto.LoginResponse;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.shared.exception.auth.InvalidCredentialsException;
import com.udea.digitalbank.shared.exception.auth.UserNotEnabledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pruebas de flujo (AAA) del inicio de sesión: {@link AuthService} y {@link UserStatusService} reales sobre un
 * usuario en memoria, con BCrypt real. Verifican el conteo de intentos fallidos a lo largo de varios intentos
 * seguidos, que una prueba aislada de cada método no muestra. El usuario se bloquea en el tercer intento fallido
 * (HU02 CA04 y HU09 CA01 ajustadas al comportamiento del backend).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU02 / HU09 - Flujo de inicio de sesión e intentos fallidos")
class LoginFlowTest {

    private static final String EMAIL = "maria.lopez@example.com";
    private static final String PASSWORD = "Clave#2026";
    private static final String GENERIC_MESSAGE = "Email o contraseña incorrectos";

    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private VerificationService verificationService;
    @Mock
    private VerificationDispatcher verificationDispatcher;
    @Mock
    private Catalogs catalogs;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        for (UserStatusEnum status : UserStatusEnum.values()) {
            lenient().when(catalogs.status(status)).thenReturn(AuthFixtures.status(status));
        }
        UserStatusService userStatusService = new UserStatusService(userRepository, sessionService, catalogs);
        authService = new AuthService(userRepository, sessionService, passwordEncoder, verificationService,
                verificationDispatcher, userStatusService);
        user = AuthFixtures.customer(EMAIL, passwordEncoder.encode(PASSWORD), UserStatusEnum.ACTIVE);
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(verificationDispatcher.issueAndSend(any(User.class), eq(PurposeEnum.LOGIN)))
                .thenAnswer(invocation -> new IssuedChallenge(UUID.randomUUID(), "123456", 5));
    }

    private static LoginRequest request(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private Throwable failLogin() {
        return catchThrowable(() -> authService.login(request(EMAIL, "Incorrecta#1")));
    }

    @Test
    @DisplayName("HU02 CA02 / HU09 CA02 - Un intento fallido se rechaza con mensaje genérico, se registra y no bloquea")
    void deberiaRegistrarElIntentoFallidoSinBloquear() {
        // Act
        Throwable error = failLogin();

        // Assert
        assertThat(error).isInstanceOf(InvalidCredentialsException.class).hasMessage(GENERIC_MESSAGE);
        assertThat(user.getFailedAttempts()).isEqualTo((short) 1);
        assertThat(user.getStatus().is(UserStatusEnum.ACTIVE)).isTrue();
    }

    @Test
    @DisplayName("HU02 CA04 / HU09 CA01 - El tercer intento fallido bloquea al usuario y cierra su sesión")
    void deberiaBloquearAlTercerIntentoFallido() {
        // Arrange
        failLogin();
        failLogin();
        boolean activeAfterTwo = user.getStatus().is(UserStatusEnum.ACTIVE);

        // Act
        Throwable third = failLogin();

        // Assert
        assertThat(activeAfterTwo).isTrue();
        assertThat(third).isInstanceOf(InvalidCredentialsException.class);
        assertThat(user.getStatus().is(UserStatusEnum.BLOCKED)).isTrue();
        verify(sessionService).revoke(user.getId());
    }

    @Test
    @DisplayName("HU02 CA05 / HU09 CA01 - Bloqueado, ni la contraseña correcta permite entrar ni se envía código 2FA")
    void deberiaImpedirElAccesoMientrasEsteBloqueado() {
        // Arrange
        failLogin();
        failLogin();
        failLogin();

        // Act + Assert
        assertThatThrownBy(() -> authService.login(request(EMAIL, PASSWORD)))
                .isInstanceOf(UserNotEnabledException.class)
                .hasMessageContaining("bloqueado");
        verify(verificationDispatcher, never()).issueAndSend(any(User.class), any(PurposeEnum.class));
    }

    @Test
    @DisplayName("HU09 CA02 - Un inicio de sesión correcto reinicia el conteo: dos fallos más no bloquean")
    void deberiaReiniciarElConteoConUnInicioCorrecto() {
        // Arrange
        failLogin();
        failLogin();

        // Act
        LoginResponse response = authService.login(request(EMAIL, PASSWORD));
        failLogin();
        failLogin();

        // Assert
        assertThat(response.isRequiresTwoFactor()).isTrue();
        assertThat(user.getFailedAttempts()).isEqualTo((short) 2);
        assertThat(user.getStatus().is(UserStatusEnum.ACTIVE)).isTrue();
    }

    @Test
    @DisplayName("HU02 CA03 - Un correo no registrado recibe el mismo mensaje que una contraseña incorrecta")
    void deberiaResponderIgualAUnCorreoNoRegistrado() {
        // Arrange
        String unknown = "nadie@example.com";
        lenient().when(userRepository.findByEmail(unknown)).thenReturn(Optional.empty());

        // Act
        Throwable unknownEmail = catchThrowable(() -> authService.login(request(unknown, PASSWORD)));
        Throwable wrongPassword = failLogin();

        // Assert
        assertThat(unknownEmail).isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(wrongPassword.getMessage());
    }

    @Test
    @DisplayName("HU02 CA01 / HU03 CA01 - Credenciales válidas: no hay token todavía, se envía el código 2FA")
    void deberiaPedirElSegundoFactorConCredencialesValidas() {
        // Act
        LoginResponse response = authService.login(request(EMAIL, PASSWORD));

        // Assert
        assertThat(response.isRequiresTwoFactor()).isTrue();
        assertThat(response.getChallengeId()).isNotNull();
        verify(verificationDispatcher).issueAndSend(user, PurposeEnum.LOGIN);
        verify(sessionService, never()).start(any(User.class));
    }

    @Test
    @DisplayName("HU02 CA07 - Un cliente pendiente de revisión no entra aunque la contraseña sea correcta")
    void deberiaRechazarAUnClientePendienteDeRevision() {
        // Arrange
        user.setStatus(AuthFixtures.status(UserStatusEnum.PENDING_REVIEW));

        // Act + Assert
        assertThatThrownBy(() -> authService.login(request(EMAIL, PASSWORD)))
                .isInstanceOf(UserNotEnabledException.class)
                .hasMessageContaining("revisión");
        verify(sessionService, never()).revoke(any());
        verify(verificationDispatcher, never()).issueAndSend(any(User.class), any(PurposeEnum.class));
    }
}
