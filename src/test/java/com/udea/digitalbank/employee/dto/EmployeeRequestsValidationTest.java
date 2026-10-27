package com.udea.digitalbank.employee.dto;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Validación de los DTO de empleados")
class EmployeeRequestsValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Transversal - Perfil: todo es opcional, pero un nombre presente no puede estar en blanco ni pasar de 100")
    void validaLosNombres() {
        UpdateEmployeeProfileRequest request = new UpdateEmployeeProfileRequest();
        assertThat(invalidFields(request)).isEmpty();

        request.setFirstNames("   ");
        request.setLastNames("x".repeat(101));
        assertThat(invalidFields(request)).containsExactlyInAnyOrder("firstNames", "lastNames");

        request.setFirstNames("Ana");
        request.setLastNames("Gómez");
        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    @DisplayName("Transversal - Estado: exige el estado")
    void validaElCambioDeEstado() {
        ChangeEmployeeStatusRequest request = new ChangeEmployeeStatusRequest();
        assertThat(invalidFields(request)).containsExactly("status");

        request.setStatus(UserStatusEnum.BLOCKED);
        assertThat(invalidFields(request)).isEmpty();
    }
}
