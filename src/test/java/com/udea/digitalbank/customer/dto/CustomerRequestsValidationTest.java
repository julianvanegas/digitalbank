package com.udea.digitalbank.customer.dto;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Validación de los DTO de clientes")
class CustomerRequestsValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("HU07 CA02 - Perfil: todo es opcional, pero un nombre presente no puede estar en blanco ni pasar de 100")
    void validaLosNombres() {
        UpdateCustomerProfileRequest request = new UpdateCustomerProfileRequest();
        assertThat(invalidFields(request)).isEmpty();

        request.setFirstNames("   ");
        request.setLastNames("x".repeat(101));
        assertThat(invalidFields(request)).containsExactlyInAnyOrder("firstNames", "lastNames");

        request.setFirstNames("Ana");
        request.setLastNames("Gómez");
        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    @DisplayName("HU07 CA02 - Perfil: un nombre solo con espacios, tabulaciones o saltos de línea está en blanco")
    void rechazaLosNombresEnBlancoDeCualquierTipo() {
        UpdateCustomerProfileRequest request = new UpdateCustomerProfileRequest();

        for (String blank : new String[] {"", " ", "\t", " \n ", "\r\n"}) {
            request.setFirstNames(blank);
            assertThat(invalidFields(request)).containsExactly("firstNames");
        }
    }

    @Test
    @DisplayName("HU07 CA02 - Perfil: un nombre con texto es válido aunque tenga espacios alrededor")
    void aceptaLosNombresConTexto() {
        UpdateCustomerProfileRequest request = new UpdateCustomerProfileRequest();

        for (String name : new String[] {"  Ana  ", "Ana María"}) {
            request.setFirstNames(name);
            assertThat(invalidFields(request)).isEmpty();
        }
    }

    @Test
    @DisplayName("HU07 CA02 - Perfil: un nombre con saltos de línea se rechaza, aunque tenga texto")
    void rechazaLosNombresConSaltosDeLinea() {
        UpdateCustomerProfileRequest request = new UpdateCustomerProfileRequest();

        for (String name : new String[] {"\nAna", "Ana\n", "Ana\nGómez", "Ana\r\nGómez", "Ana\u2028Gómez"}) {
            request.setFirstNames(name);
            assertThat(invalidFields(request)).containsExactly("firstNames");
        }
    }

    @Test
    @DisplayName("HU07 CA02 - Perfil: el teléfono, si viene, debe tener formato válido")
    void validaElTelefono() {
        UpdateCustomerProfileRequest request = new UpdateCustomerProfileRequest();
        request.setPhone("no-es-un-telefono");
        assertThat(invalidFields(request)).containsExactly("phone");

        request.setPhone("+573001234567");
        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    @DisplayName("HU08 CA04 - Estado: exige el tipo y el número de documento y el estado")
    void validaElCambioDeEstado() {
        ChangeCustomerStatusRequest request = new ChangeCustomerStatusRequest();
        assertThat(invalidFields(request)).containsExactlyInAnyOrder("documentTypeId", "documentNumber", "status");

        request.setDocumentTypeId((short) 1);
        request.setDocumentNumber("1000000001");
        request.setStatus(UserStatusEnum.ACTIVE);
        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    @DisplayName("HU07 CA02 - UpdateCustomerProfileRequest: los nombres se guardan sin espacios sobrantes")
    void normalizaLosNombresDeUpdateCustomerProfileRequest() {
        UpdateCustomerProfileRequest request = new UpdateCustomerProfileRequest();

        request.setFirstNames("    Ana     Vélez  ");
        request.setLastNames("  Gómez    Ruiz ");

        assertThat(request.getFirstNames()).isEqualTo("Ana Vélez");
        assertThat(request.getLastNames()).isEqualTo("Gómez Ruiz");
    }

    @Test
    @DisplayName("HU01 - CreateCustomerRequest: los nombres se guardan sin espacios sobrantes")
    void normalizaLosNombresDeCreateCustomerRequest() {
        CreateCustomerRequest request = new CreateCustomerRequest();

        request.setFirstNames("    Ana     Vélez  ");
        request.setLastNames("  Gómez    Ruiz ");

        assertThat(request.getFirstNames()).isEqualTo("Ana Vélez");
        assertThat(request.getLastNames()).isEqualTo("Gómez Ruiz");
    }
}
