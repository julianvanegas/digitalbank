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
 * Pruebas unitarias del validator {@link Phone}: formato E.164 simplificado, obligatorio
 * en el registro (HU01) y opcional en la actualización de perfil (HU07).
 */
class PhoneConstraintTest {

    @AllArgsConstructor
    static class PhoneHolder {
        @Phone
        String phone;
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
    @DisplayName("Números que cumplen el formato E.164 simplificado")
    class NumerosValidos {

        @ParameterizedTest(name = "\"{0}\" es un teléfono válido")
        @ValueSource(strings = {
                "+1234567",         // 7 dígitos: límite inferior
                "+123456789012345", // 15 dígitos: límite superior
                "+573001234567",   // ejemplo típico colombiano
                "+34911234567"
        })
        void deberiaAceptarTelefonosConFormatoCorrecto(String phone) {
            // Arrange
            PhoneHolder holder = new PhoneHolder(phone);

            // Act
            Set<ConstraintViolation<PhoneHolder>> violaciones = validator.validate(holder);

            // Assert
            assertThat(violaciones).isEmpty();
        }

        @Test
        @DisplayName("Un valor nulo es válido (se combina con @NotBlank donde sea obligatorio)")
        void deberiaAceptarValorNulo() {
            // Arrange
            PhoneHolder holder = new PhoneHolder(null);

            // Act
            Set<ConstraintViolation<PhoneHolder>> violaciones = validator.validate(holder);

            // Assert
            assertThat(violaciones).isEmpty();
        }
    }

    @Nested
    @DisplayName("Números que deben ser rechazados")
    class NumerosInvalidos {

        @ParameterizedTest(name = "\"{0}\" debe ser rechazado")
        @ValueSource(strings = {
                "3001234567",       // falta el "+"
                "+0301234567",      // el primer dígito no puede ser 0
                "+123456",          // 6 dígitos: por debajo del mínimo (7)
                "+1234567890123456",// 16 dígitos: por encima del máximo (15)
                "+57 300 123 4567", // contiene espacios
                "+57-300-1234567",  // contiene guiones
                "+57abc1234567"     // contiene letras
        })
        void deberiaRechazarTelefonosConFormatoIncorrecto(String phone) {
            // Arrange
            PhoneHolder holder = new PhoneHolder(phone);

            // Act
            Set<ConstraintViolation<PhoneHolder>> violaciones = validator.validate(holder);

            // Assert
            assertThat(violaciones).isNotEmpty();
        }
    }
}