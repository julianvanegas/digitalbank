package com.udea.digitalbank.accounts.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de validación (AAA) de las solicitudes de bloqueo y desbloqueo de cuentas: el motivo es obligatorio y
 * tiene como máximo 255 caracteres, igual que la columna reason de account_actions.
 */
@DisplayName("HU15 / HU16 - Validación de las solicitudes de bloqueo y desbloqueo")
class AccountRequestsValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("HU15 CA01 - Un bloqueo con motivo no tiene errores")
    void deberiaAceptarUnBloqueoConMotivo() {
        // Arrange
        BlockAccountRequest request = new BlockAccountRequest();
        request.setReason("Alerta de fraude");

        // Act + Assert
        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest(name = "motivo \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("HU15 CA03 - Sin motivo, vacío o en blanco, el bloqueo indica que el motivo es obligatorio")
    void deberiaExigirElMotivoDelBloqueo(String reason) {
        // Arrange
        BlockAccountRequest request = new BlockAccountRequest();
        request.setReason(reason);

        // Act
        Set<ConstraintViolation<BlockAccountRequest>> violations = validator.validate(request);

        // Assert
        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("reason");
            assertThat(v.getMessage()).isEqualTo("El motivo del bloqueo es obligatorio");
        });
    }

    @Test
    @DisplayName("HU16 CA01 - Una reactivación con motivo no tiene errores")
    void deberiaAceptarUnaReactivacionConMotivo() {
        // Arrange
        UnblockAccountRequest request = new UnblockAccountRequest();
        request.setReason("Identidad del titular verificada");

        // Act + Assert
        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest(name = "motivo \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("HU16 CA02 - Sin motivo, vacío o en blanco, la reactivación indica que el motivo es obligatorio")
    void deberiaExigirElMotivoDeLaReactivacion(String reason) {
        // Arrange
        UnblockAccountRequest request = new UnblockAccountRequest();
        request.setReason(reason);

        // Act
        Set<ConstraintViolation<UnblockAccountRequest>> violations = validator.validate(request);

        // Assert
        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("reason");
            assertThat(v.getMessage()).isEqualTo("El motivo del desbloqueo es obligatorio");
        });
    }

    @Test
    @DisplayName("Transversal - El motivo admite hasta 255 caracteres y rechaza uno más")
    void deberiaLimitarElMotivoA255Caracteres() {
        // Arrange
        BlockAccountRequest atLimit = new BlockAccountRequest();
        atLimit.setReason("m".repeat(255));
        UnblockAccountRequest overLimit = new UnblockAccountRequest();
        overLimit.setReason("m".repeat(256));

        // Act + Assert
        assertThat(validator.validate(atLimit)).isEmpty();
        assertThat(validator.validate(overLimit)).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("reason");
    }
}
