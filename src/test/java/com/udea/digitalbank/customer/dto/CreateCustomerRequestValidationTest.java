package com.udea.digitalbank.customer.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de validación (AAA) de {@link CreateCustomerRequest}: son las reglas que @Valid aplica en el controller
 * antes de llegar al servicio. El registro lo usan el cliente (HU01) y el administrador (HU27).
 * La edad mínima no se valida aquí sino en el servicio (ver CustomerServiceTest).
 */
@DisplayName("HU01 / HU27 - Validación de CreateCustomerRequest")
class CreateCustomerRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static CreateCustomerRequest valid() {
        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setFirstNames("Maria Fernanda");
        request.setLastNames("Lopez Gomez");
        request.setDocumentTypeId((short) 1);
        request.setDocumentNumber("1032456123");
        request.setPhone("+573001112233");
        request.setBirthDate(LocalDate.of(1990, 5, 14));
        request.setEmail("maria.lopez@example.com");
        return request;
    }

    private Set<String> invalidFields(CreateCustomerRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private Set<String> invalidFieldsAfter(Consumer<CreateCustomerRequest> change) {
        CreateCustomerRequest request = valid();
        change.accept(request);
        return invalidFields(request);
    }

    @Test
    @DisplayName("HU01 CA01 / HU27 CA01 - Una solicitud con todos los datos válidos no tiene errores")
    void deberiaAceptarUnaSolicitudCompleta() {
        // Act + Assert
        assertThat(invalidFields(valid())).isEmpty();
    }

    @Test
    @DisplayName("HU01 CA11 / HU27 CA02 - Sin ningún dato se indican los 7 campos obligatorios")
    void deberiaIndicarTodosLosCamposObligatorios() {
        // Act
        Set<String> fields = invalidFields(new CreateCustomerRequest());

        // Assert
        assertThat(fields).containsExactlyInAnyOrder("firstNames", "lastNames", "documentTypeId",
                "documentNumber", "phone", "birthDate", "email");
    }

    @Test
    @DisplayName("HU01 CA11 / HU27 CA02 - Cada dato omitido se indica por su nombre y solo ese")
    void deberiaIndicarCadaCampoOmitido() {
        // Act + Assert
        assertThat(invalidFieldsAfter(r -> r.setFirstNames(null))).containsExactly("firstNames");
        assertThat(invalidFieldsAfter(r -> r.setLastNames("  "))).containsExactly("lastNames");
        assertThat(invalidFieldsAfter(r -> r.setDocumentTypeId(null))).containsExactly("documentTypeId");
        assertThat(invalidFieldsAfter(r -> r.setDocumentNumber(""))).containsExactly("documentNumber");
        assertThat(invalidFieldsAfter(r -> r.setPhone(null))).containsExactly("phone");
        assertThat(invalidFieldsAfter(r -> r.setBirthDate(null))).containsExactly("birthDate");
        assertThat(invalidFieldsAfter(r -> r.setEmail(" "))).containsExactly("email");
    }

    @ParameterizedTest(name = "correo \"{0}\"")
    @ValueSource(strings = {"maria.example.com", "maria@", "@example.com", "maria lopez@example.com"})
    @DisplayName("HU01 CA12 - Un correo con formato inválido se rechaza")
    void deberiaRechazarUnCorreoInvalido(String email) {
        // Act + Assert
        assertThat(invalidFieldsAfter(r -> r.setEmail(email))).containsExactly("email");
    }

    @Test
    @DisplayName("HU01 CA12 - Un teléfono sin formato internacional se rechaza")
    void deberiaRechazarUnTelefonoInvalido() {
        // Act + Assert
        assertThat(invalidFieldsAfter(r -> r.setPhone("3001112233"))).containsExactly("phone");
    }

    @Test
    @DisplayName("HU01 CA12 - Nombres de más de 100 caracteres y documento de más de 20 se rechazan")
    void deberiaRechazarDatosDemasiadoLargos() {
        // Act
        Set<String> fields = invalidFieldsAfter(r -> {
            r.setFirstNames("a".repeat(101));
            r.setLastNames("b".repeat(101));
            r.setDocumentNumber("1".repeat(21));
        });

        // Assert
        assertThat(fields).containsExactlyInAnyOrder("firstNames", "lastNames", "documentNumber");
    }

    @Test
    @DisplayName("HU01 CA12 - En los límites (100 caracteres en nombres, 20 en documento) se acepta")
    void deberiaAceptarLosLimites() {
        // Act
        Set<String> fields = invalidFieldsAfter(r -> {
            r.setFirstNames("a".repeat(100));
            r.setLastNames("b".repeat(100));
            r.setDocumentNumber("1".repeat(20));
        });

        // Assert
        assertThat(fields).isEmpty();
    }
}
