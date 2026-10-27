package com.udea.digitalbank.auth.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de validación (AAA) de las solicitudes de autenticación: son las reglas que @Valid aplica en
 * AuthController antes de llegar a los servicios.
 */
@DisplayName("Validación de las solicitudes de autenticación")
class AuthRequestsValidationTest {

    private static final String VALID_PASSWORD = "Clave#2026";

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Nested
    @DisplayName("HU02 - LoginRequest")
    class Login {

        @Test
        @DisplayName("HU02 CA01 - Correo y contraseña presentes no tienen errores")
        void deberiaAceptarCorreoYContrasena() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setEmail("maria.lopez@example.com");
            request.setPassword("cualquiera");

            // Act + Assert
            assertThat(invalidFields(request)).isEmpty();
        }

        @Test
        @DisplayName("HU02 CA02 - Sin correo ni contraseña se indican ambos campos")
        void deberiaExigirCorreoYContrasena() {
            // Act + Assert
            assertThat(invalidFields(new LoginRequest())).containsExactlyInAnyOrder("email", "password");
        }
    }

    @Nested
    @DisplayName("HU03 - VerifyTwoFactorRequest y ResendTwoFactorRequest")
    class TwoFactor {

        @Test
        @DisplayName("HU03 CA02 - Con el id del reto y el código no hay errores")
        void deberiaAceptarRetoYCodigo() {
            // Arrange
            VerifyTwoFactorRequest request = new VerifyTwoFactorRequest();
            request.setChallengeId(UUID.randomUUID());
            request.setCode("123456");

            // Act + Assert
            assertThat(invalidFields(request)).isEmpty();
        }

        @Test
        @DisplayName("HU03 CA03 - Sin id del reto ni código se indican ambos campos")
        void deberiaExigirRetoYCodigo() {
            // Act + Assert
            assertThat(invalidFields(new VerifyTwoFactorRequest())).containsExactlyInAnyOrder("challengeId", "code");
        }

        @Test
        @DisplayName("HU03 CA07 - El reenvío exige el id del reto")
        void deberiaExigirElRetoParaReenviar() {
            // Act + Assert
            assertThat(invalidFields(new ResendTwoFactorRequest())).containsExactly("challengeId");
        }
    }

    @Nested
    @DisplayName("HU01 / HU27 - VerifyEmailRequest (confirmar correo y definir contraseña)")
    class VerifyEmail {

        private VerifyEmailRequest request(String password) {
            VerifyEmailRequest request = new VerifyEmailRequest();
            request.setEmail("maria.lopez@example.com");
            request.setCode("123456");
            request.setPassword(password);
            request.setConfirmPassword(password);
            return request;
        }

        @Test
        @DisplayName("HU01 CA08 / HU27 CA06 - Una contraseña que cumple la política se acepta")
        void deberiaAceptarUnaContrasenaValida() {
            // Act + Assert
            assertThat(invalidFields(request(VALID_PASSWORD))).isEmpty();
        }

        @ParameterizedTest(name = "contraseña \"{0}\"")
        @ValueSource(strings = {"Ab1#xyz", "Abcdef1#Abcdef1#X"})
        @DisplayName("HU01 CA09 - Menos de 8 o más de 16 caracteres se rechaza")
        void deberiaRechazarLongitudInvalida(String password) {
            // Act + Assert
            assertThat(invalidFields(request(password))).containsExactly("password");
        }

        @ParameterizedTest(name = "contraseña \"{0}\"")
        @ValueSource(strings = {"clave#2026", "CLAVE#2026", "Clave#Clave", "Clave2026"})
        @DisplayName("HU01 CA10 - Sin mayúscula, minúscula, número o carácter especial se rechaza")
        void deberiaRechazarComposicionInvalida(String password) {
            // Act + Assert
            assertThat(invalidFields(request(password))).containsExactly("password");
        }

        @Test
        @DisplayName("HU01 CA11 - Sin datos se indican el correo, el código, la contraseña y su confirmación")
        void deberiaExigirTodosLosCampos() {
            // Act + Assert
            assertThat(invalidFields(new VerifyEmailRequest()))
                    .containsExactlyInAnyOrder("email", "code", "password", "confirmPassword");
        }
    }

    @Nested
    @DisplayName("HU05 - EmailRequest y PasswordResetRequest")
    class PasswordReset {

        private PasswordResetRequest request(String password) {
            PasswordResetRequest request = new PasswordResetRequest();
            request.setEmail("maria.lopez@example.com");
            request.setCode("123456");
            request.setPassword(password);
            return request;
        }

        @Test
        @DisplayName("HU05 CA01 - La solicitud de recuperación exige un correo con formato válido")
        void deberiaExigirUnCorreoValido() {
            // Arrange
            EmailRequest request = new EmailRequest();

            // Act
            Set<String> empty = invalidFields(request);
            request.setEmail("no-es-un-correo");
            Set<String> malformed = invalidFields(request);
            request.setEmail("maria.lopez@example.com");
            Set<String> valid = invalidFields(request);

            // Assert
            assertThat(empty).containsExactly("email");
            assertThat(malformed).containsExactly("email");
            assertThat(valid).isEmpty();
        }

        @Test
        @DisplayName("HU05 CA08 - Correo, código y nueva contraseña válidos no tienen errores")
        void deberiaAceptarUnRestablecimientoValido() {
            // Act + Assert
            assertThat(invalidFields(request(VALID_PASSWORD))).isEmpty();
        }

        @ParameterizedTest(name = "contraseña \"{0}\"")
        @ValueSource(strings = {"Ab1#xyz", "Abcdef1#Abcdef1#X", "clave#2026", "Clave2026"})
        @DisplayName("HU05 CA09 - Una nueva contraseña que no cumple la política se rechaza")
        void deberiaRechazarUnaContrasenaFueraDePolitica(String password) {
            // Act + Assert
            assertThat(invalidFields(request(password))).containsExactly("password");
        }
    }
}
