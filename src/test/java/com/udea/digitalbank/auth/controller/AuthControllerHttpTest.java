package com.udea.digitalbank.auth.controller;

import com.udea.digitalbank.auth.dto.AuthResponse;
import com.udea.digitalbank.auth.dto.LoginRequest;
import com.udea.digitalbank.auth.dto.LoginResponse;
import com.udea.digitalbank.auth.service.AuthService;
import com.udea.digitalbank.auth.service.EmailVerificationService;
import com.udea.digitalbank.auth.service.PasswordResetService;
import com.udea.digitalbank.shared.exception.InvalidRequestException;
import com.udea.digitalbank.shared.exception.TooManyRequestsException;
import com.udea.digitalbank.shared.exception.auth.InvalidCredentialsException;
import com.udea.digitalbank.shared.exception.auth.InvalidVerificationCodeException;
import com.udea.digitalbank.shared.exception.auth.UserNotEnabledException;
import com.udea.digitalbank.support.HttpTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.udea.digitalbank.support.HttpTestSupport.authenticate;
import static com.udea.digitalbank.support.HttpTestSupport.body;
import static com.udea.digitalbank.support.HttpTestSupport.clearAuthentication;
import static com.udea.digitalbank.support.HttpTestSupport.perform;
import static com.udea.digitalbank.support.HttpTestSupport.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas de contrato HTTP (AAA) de {@link AuthController}: qué código y qué mensaje recibe el cliente de la API en
 * los criterios de inicio de sesión, 2FA, sesión, recuperación de contraseña y activación.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU02 / HU03 / HU04 / HU05 / HU27 - Contrato HTTP de autenticación")
class AuthControllerHttpTest {

    private static final String BASE = "/api/v1/auth";
    private static final String LOGIN_BODY = "{\"email\":\"maria.lopez@example.com\",\"password\":\"Clave#2026\"}";

    @Mock
    private AuthService authService;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private PasswordResetService passwordResetService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = HttpTestSupport.mockMvc(
                new AuthController(authService, emailVerificationService, passwordResetService));
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    @Test
    @DisplayName("HU02 CA01 / HU03 CA08 - Credenciales válidas: 200 que pide el 2FA, sin token todavía")
    void deberiaPedirElSegundoFactor() throws Exception {
        // Arrange
        UUID challengeId = UUID.randomUUID();
        when(authService.login(any(LoginRequest.class))).thenReturn(new LoginResponse(true, challengeId));

        // Act
        MockHttpServletResponse response = perform(mockMvc, post(BASE + "/login", LOGIN_BODY));

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response)).contains("\"requiresTwoFactor\":true").contains(challengeId.toString())
                .doesNotContain("token");
    }

    @Test
    @DisplayName("HU02 CA02 / HU02 CA03 - Credenciales inválidas: 401 con mensaje genérico")
    void deberiaResponder401ConMensajeGenerico() throws Exception {
        // Arrange
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Email o contraseña incorrectos"));

        // Act
        MockHttpServletResponse response = perform(mockMvc, post(BASE + "/login", LOGIN_BODY));

        // Assert
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body(response)).contains("Email o contraseña incorrectos");
    }

    @Test
    @DisplayName("HU02 CA05 - Cliente bloqueado: 403 que informa que el acceso está bloqueado")
    void deberiaInformarQueElAccesoEstaBloqueado() throws Exception {
        // Arrange
        when(authService.login(any(LoginRequest.class))).thenThrow(
                new UserNotEnabledException("El usuario está bloqueado, contacta a un administrador"));

        // Act
        MockHttpServletResponse response = perform(mockMvc, post(BASE + "/login", LOGIN_BODY));

        // Assert
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body(response)).contains("bloqueado");
    }

    @Test
    @DisplayName("HU02 CA01 - Sin correo ni contraseña: 400 y no se intenta el inicio de sesión")
    void deberiaExigirLasCredenciales() throws Exception {
        // Act
        MockHttpServletResponse response = perform(mockMvc, post(BASE + "/login", "{}"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(body(response)).contains("email").contains("password");
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("HU03 CA02 - Código 2FA correcto: 200 con el token de la sesión")
    void deberiaEntregarElToken() throws Exception {
        // Arrange
        UUID challengeId = UUID.randomUUID();
        when(authService.verifyTwoFactor(challengeId, "123456")).thenReturn(new AuthResponse("jwt-de-prueba"));

        // Act
        MockHttpServletResponse response = perform(mockMvc, post(BASE + "/verify-2fa",
                "{\"challengeId\":\"" + challengeId + "\",\"code\":\"123456\"}"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response)).contains("jwt-de-prueba");
    }

    @Test
    @DisplayName("HU03 CA03 / HU03 CA04 - Código incorrecto o vencido: 400 con mensaje de código inválido o expirado")
    void deberiaRechazarUnCodigoInvalido() throws Exception {
        // Arrange
        UUID challengeId = UUID.randomUUID();
        when(authService.verifyTwoFactor(eq(challengeId), anyString()))
                .thenThrow(new InvalidVerificationCodeException("Código de verificación inválido o expirado"));

        // Act
        MockHttpServletResponse response = perform(mockMvc, post(BASE + "/verify-2fa",
                "{\"challengeId\":\"" + challengeId + "\",\"code\":\"000000\"}"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(body(response)).contains("inválido o expirado");
    }

    @Test
    @DisplayName("HU03 CA07 - Reenvío antes del intervalo: 429 con la cabecera Retry-After")
    void deberiaPedirEsperarAntesDeReenviar() throws Exception {
        // Arrange
        UUID challengeId = UUID.randomUUID();
        when(authService.resendTwoFactor(challengeId))
                .thenThrow(new TooManyRequestsException("Espera 42 segundos antes de pedir otro código", 42));

        // Act
        MockHttpServletResponse response = perform(mockMvc, post(BASE + "/resend-2fa",
                "{\"challengeId\":\"" + challengeId + "\"}"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("42");
    }

    @Test
    @DisplayName("HU04 CA01 - Cerrar sesión: 204 y se revoca la sesión del usuario autenticado")
    void deberiaCerrarLaSesion() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        authenticate(userId, false);

        // Act
        MockHttpServletResponse response = perform(mockMvc, post(BASE + "/logout", ""));

        // Assert
        assertThat(response.getStatus()).isEqualTo(204);
        verify(authService).logout(userId);
    }

    @Test
    @DisplayName("HU05 CA01 - Recuperar contraseña: 204 sin cuerpo, igual exista o no el correo")
    void deberiaResponderIgualExistaONoElCorreo() throws Exception {
        // Act
        MockHttpServletResponse registered = perform(mockMvc, post(BASE + "/recover-password",
                "{\"email\":\"maria.lopez@example.com\"}"));
        MockHttpServletResponse unknown = perform(mockMvc, post(BASE + "/recover-password",
                "{\"email\":\"nadie@example.com\"}"));

        // Assert
        assertThat(registered.getStatus()).isEqualTo(204);
        assertThat(unknown.getStatus()).isEqualTo(204);
        assertThat(body(registered)).isEqualTo(body(unknown)).isEmpty();
    }

    @Test
    @DisplayName("HU05 CA09 - Nueva contraseña que no cumple la política: 400 y no se toca la contraseña")
    void deberiaRechazarUnaContrasenaDebil() throws Exception {
        // Act
        MockHttpServletResponse response = perform(mockMvc, post(BASE + "/reset-password",
                "{\"email\":\"maria.lopez@example.com\",\"code\":\"123456\",\"password\":\"clave\"}"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(body(response)).contains("password");
        verifyNoInteractions(passwordResetService);
    }

    @Test
    @DisplayName("HU05 CA08 - Código válido y contraseña válida: 204")
    void deberiaRestablecerLaContrasena() throws Exception {
        // Act
        MockHttpServletResponse response = perform(mockMvc, post(BASE + "/reset-password",
                "{\"email\":\"maria.lopez@example.com\",\"code\":\"123456\",\"password\":\"Nueva#2026\"}"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(204);
        verify(passwordResetService).resetPassword("maria.lopez@example.com", "123456", "Nueva#2026");
    }

    @Test
    @DisplayName("HU27 CA06 - Activación con contraseña y confirmación distintas: 400 que lo informa")
    void deberiaRechazarContrasenasQueNoCoinciden() throws Exception {
        // Arrange
        doThrow(new InvalidRequestException("La contraseña y su confirmación no coinciden"))
                .when(emailVerificationService).verifyEmail("laura.rojas@example.com", "123456",
                        "Clave#2026", "Clave#2027");

        // Act
        MockHttpServletResponse response = perform(mockMvc, post(BASE + "/verify-email",
                "{\"email\":\"laura.rojas@example.com\",\"code\":\"123456\","
                        + "\"password\":\"Clave#2026\",\"confirmPassword\":\"Clave#2027\"}"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(body(response)).contains("no coinciden");
    }

    @Test
    @DisplayName("HU27 CA05 - Reenviar el código de activación: 204 sin revelar si el correo existe")
    void deberiaAceptarElReenvioDeActivacion() throws Exception {
        // Act
        MockHttpServletResponse response = perform(mockMvc, post(BASE + "/resend-verification",
                "{\"email\":\"laura.rojas@example.com\"}"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(204);
        verify(emailVerificationService).resendVerification("laura.rojas@example.com");
    }
}
