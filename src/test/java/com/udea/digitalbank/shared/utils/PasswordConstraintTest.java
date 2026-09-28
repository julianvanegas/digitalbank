package com.udea.digitalbank.shared.utils;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.AllArgsConstructor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias del validator {@link Password}, que aplica la política de contraseña
 * definida en HU01 (registro) y reutilizada en HU05 (recuperación de contraseña):
 * 8-16 caracteres, con al menos una mayúscula, una minúscula, un número y un carácter especial.
 * No requiere mocks: se valida directamente contra el motor de Bean Validation (jakarta.validation).
 */
class PasswordConstraintTest {

    // Envoltorio mínimo para poder aplicar la anotación @Password a un campo y validarlo
    @AllArgsConstructor
    static class PasswordHolder {
        @Password
        String password;
    }

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Nested
    @DisplayName("CA08 / CA12 / CA18 - Contraseñas que cumplen la política")
    class ContraseñasValidas {

        @ParameterizedTest(name = "\"{0}\" es una contraseña válida")
        @ValueSource(strings = {
                "Abcdef1$",       // 8 caracteres exactos (límite inferior, CA18)
                "Abcdefghijklm1$", // 15 caracteres
                "Abcdefghijklmn1$", // 16 caracteres exactos (límite superior, CA18)
                "Password1!",
                "MyBank#2026",
                "S3guro_Clave"
        })
        void deberiaAceptarContraseñasQueCumplenTodosLosRequisitos(String password) {
            // Arrange
            PasswordHolder holder = new PasswordHolder(password);

            // Act
            Set<ConstraintViolation<PasswordHolder>> violaciones = validator.validate(holder);

            // Assert
            assertThat(violaciones).isEmpty();
        }

        @Test
        @DisplayName("Un valor nulo es válido (se combina con @NotBlank donde sea obligatoria)")
        void deberiaAceptarValorNulo() {
            // Arrange
            PasswordHolder holder = new PasswordHolder(null);

            // Act
            Set<ConstraintViolation<PasswordHolder>> violaciones = validator.validate(holder);

            // Assert
            assertThat(violaciones).isEmpty();
        }
    }

    @Nested
    @DisplayName("CA09 / CA13 - Longitud fuera del rango permitido")
    class LongitudInvalida {

        @ParameterizedTest(name = "\"{0}\" debe ser rechazada por longitud")
        @ValueSource(strings = {
                "Abc1$",              // 5 caracteres: por debajo del mínimo (7)
                "Abcdef1",            // 7 caracteres: un caracter por debajo del mínimo (le falta el especial además)
                "Abcdefghijklmno1$",  // 17 caracteres: un caracter por encima del máximo
        })
        void deberiaRechazarContraseñasFueraDeRango(String password) {
            // Arrange
            PasswordHolder holder = new PasswordHolder(password);

            // Act
            Set<ConstraintViolation<PasswordHolder>> violaciones = validator.validate(holder);

            // Assert
            assertThat(violaciones).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("CA10 / CA14 - Longitud correcta pero sin todos los requisitos de composición")
    class ComposicionInvalida {

        @ParameterizedTest(name = "\"{0}\" debe ser rechazada por composición")
        @ValueSource(strings = {
                "abcdefg1$",   // sin mayúscula
                "ABCDEFG1$",   // sin minúscula
                "Abcdefgh$",   // sin número
                "Abcdefgh1",   // sin carácter especial
                "12345678",    // solo números
        })
        void deberiaRechazarContraseñasQueNoCumplenComposicion(String password) {
            // Arrange
            PasswordHolder holder = new PasswordHolder(password);

            // Act
            Set<ConstraintViolation<PasswordHolder>> violaciones = validator.validate(holder);

            // Assert
            assertThat(violaciones).isNotEmpty();
        }
    }
}