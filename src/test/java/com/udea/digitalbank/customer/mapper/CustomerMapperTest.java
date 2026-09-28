package com.udea.digitalbank.customer.mapper;

import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.customer.domain.Customer;
import com.udea.digitalbank.customer.domain.DocumentType;
import com.udea.digitalbank.customer.dto.CustomerResponse;
import com.udea.digitalbank.customer.dto.DocumentTypeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias (AAA) para CustomerMapper. Se usa la implementación real generada por
 * MapStruct (vía Mappers.getMapper), no un mock: lo que interesa es verificar el mapeo en sí.
 */
@DisplayName("CustomerMapper")
class CustomerMapperTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private CustomerMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(CustomerMapper.class);
    }

    private DocumentType buildDocumentType(short id, String name) {
        DocumentType documentType = new DocumentType();
        ReflectionTestUtils.setField(documentType, "id", id);
        ReflectionTestUtils.setField(documentType, "name", name);
        return documentType;
    }

    private Customer buildCustomer() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setUserId(USER_ID);
        customer.setFirstNames("Ana");
        customer.setLastNames("Torres");
        customer.setDocumentType(buildDocumentType((short) 1, "Cédula de ciudadanía"));
        customer.setDocumentNumber("123456789");
        customer.setPhone("+573001234567");
        customer.setBirthDate(LocalDate.of(2000, 1, 1));
        return customer;
    }

    @Test
    @DisplayName("Combina los datos del perfil (Customer) y del usuario (UserView) en una sola respuesta")
    void deberiaCombinarDatosDeCustomerYUserView() {
        // Arrange
        Customer customer = buildCustomer();
        UserView user = new UserView(USER_ID, "ana@example.com", "CUSTOMER", "ACTIVE");

        // Act
        CustomerResponse response = mapper.toResponse(customer, user);

        // Assert: datos del perfil
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getFirstNames()).isEqualTo("Ana");
        assertThat(response.getLastNames()).isEqualTo("Torres");
        assertThat(response.getDocumentNumber()).isEqualTo("123456789");
        assertThat(response.getPhone()).isEqualTo("+573001234567");
        assertThat(response.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(response.getDocumentType().getId()).isEqualTo((short) 1);
        assertThat(response.getDocumentType().getName()).isEqualTo("Cédula de ciudadanía");

        // Assert: datos del usuario, tomados de UserView y no de Customer
        assertThat(response.getUserId()).isEqualTo(USER_ID);
        assertThat(response.getEmail()).isEqualTo("ana@example.com");
        assertThat(response.getRole()).isEqualTo("CUSTOMER");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Con Customer null, deja los campos de perfil en su valor por defecto y solo mapea el usuario")
    void deberiaMapearSoloUsuarioCuandoCustomerEsNull() {
        // Arrange
        UserView user = new UserView(USER_ID, "ana@example.com", "CUSTOMER", "ACTIVE");

        // Act
        CustomerResponse response = mapper.toResponse(null, user);

        // Assert
        assertThat(response.getId()).isNull();
        assertThat(response.getFirstNames()).isNull();
        assertThat(response.getDocumentType()).isNull();
        assertThat(response.getUserId()).isEqualTo(USER_ID);
        assertThat(response.getEmail()).isEqualTo("ana@example.com");
    }

    @Test
    @DisplayName("Con UserView null, deja los campos de usuario en su valor por defecto y solo mapea el perfil")
    void deberiaMapearSoloCustomerCuandoUserEsNull() {
        // Arrange
        Customer customer = buildCustomer();

        // Act
        CustomerResponse response = mapper.toResponse(customer, null);

        // Assert
        assertThat(response.getFirstNames()).isEqualTo("Ana");
        assertThat(response.getUserId()).isNull();
        assertThat(response.getEmail()).isNull();
        assertThat(response.getRole()).isNull();
        assertThat(response.getStatus()).isNull();
    }

    @Test
    @DisplayName("Con ambos parámetros null, retorna null")
    void deberiaRetornarNullCuandoAmbosParametrosSonNull() {
        // Act
        CustomerResponse response = mapper.toResponse((Customer) null, (UserView) null);

        // Assert
        assertThat(response).isNull();
    }

    @Test
    @DisplayName("Mapea un DocumentType a su DTO correspondiente")
    void deberiaMapearDocumentType() {
        // Arrange
        DocumentType documentType = buildDocumentType((short) 2, "Pasaporte");

        // Act
        DocumentTypeResponse response = mapper.toResponse(documentType);

        // Assert
        assertThat(response.getId()).isEqualTo((short) 2);
        assertThat(response.getName()).isEqualTo("Pasaporte");
    }

    @Test
    @DisplayName("Mapear un DocumentType null retorna null")
    void deberiaRetornarNullAlMapearDocumentTypeNull() {
        // Act
        DocumentTypeResponse response = mapper.toResponse((DocumentType) null);

        // Assert
        assertThat(response).isNull();
    }
}