package com.udea.digitalbank.customer.controller;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.customer.dto.ChangeCustomerStatusRequest;
import com.udea.digitalbank.customer.dto.CreateCustomerRequest;
import com.udea.digitalbank.customer.dto.CustomerResponse;
import com.udea.digitalbank.customer.dto.DocumentTypeResponse;
import com.udea.digitalbank.customer.dto.UpdateCustomerProfileRequest;
import com.udea.digitalbank.customer.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.udea.digitalbank.shared.security.Actor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) para CustomerController. Se instancia directamente, sin MockMvc:
 * basta con verificar el código HTTP devuelto y que delega los parámetros correctos al service.
 * El usuario "autenticado" se simula con un Actor, que en producción resuelve ActorArgumentResolver
 * a partir de lo que deja JwtAuthenticationFilter.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerController")
class CustomerControllerTest {

    @Mock
    private CustomerService customerService;

    private CustomerController controller;

    @BeforeEach
    void setUp() {
        controller = new CustomerController(customerService);
    }

    @Test
    @DisplayName("create - HU01: responde 201 Created con el cliente creado")
    void create_deberiaResponder201ConClienteCreado() {
        // Arrange
        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setFirstNames("Ana");
        request.setLastNames("Torres");
        request.setDocumentTypeId((short) 1);
        request.setDocumentNumber("123456789");
        request.setPhone("+573001234567");
        request.setBirthDate(LocalDate.of(2000, 1, 1));
        request.setEmail("ana@example.com");

        CustomerResponse expected = new CustomerResponse();
        expected.setId(UUID.randomUUID());
        when(customerService.create(request)).thenReturn(expected);

        // Act
        ResponseEntity<CustomerResponse> response = controller.create(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("myProfile - HU06: usa el userId del principal autenticado y responde 200")
    void myProfile_deberiaUsarPrincipalYResponder200() {
        // Arrange
        UUID userId = UUID.randomUUID();
        CustomerResponse expected = new CustomerResponse();
        expected.setUserId(userId);
        when(customerService.getProfile(userId)).thenReturn(expected);

        // Act
        ResponseEntity<CustomerResponse> response = controller.myProfile(new Actor(userId, false));

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("updateProfile - HU07: delega userId y los 3 campos editables, responde 200")
    void updateProfile_deberiaDelegarCamposYResponder200() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UpdateCustomerProfileRequest request = new UpdateCustomerProfileRequest();
        request.setFirstNames("Nuevo Nombre");
        request.setLastNames("Nuevo Apellido");
        request.setPhone("+573111111111");

        CustomerResponse expected = new CustomerResponse();
        when(customerService.updateProfile(userId, "Nuevo Nombre", "Nuevo Apellido", "+573111111111"))
                .thenReturn(expected);

        // Act
        ResponseEntity<CustomerResponse> response = controller.updateProfile(new Actor(userId, false), request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(customerService).updateProfile(userId, "Nuevo Nombre", "Nuevo Apellido", "+573111111111");
    }

    @Test
    @DisplayName("documentTypes - responde 200 con el catálogo del servicio")
    void documentTypes_deberiaResponder200ConElCatalogo() {
        // Arrange
        List<DocumentTypeResponse> expected = List.of(new DocumentTypeResponse());
        when(customerService.getDocumentTypes()).thenReturn(expected);

        // Act
        ResponseEntity<List<DocumentTypeResponse>> response = controller.documentTypes();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getAllCustomers - delega el Pageable y responde 200 con la página resultante")
    void getAllCustomers_deberiaDelegarPageableYResponder200() {
        // Arrange
        Pageable pageable = Pageable.ofSize(20);
        Page<CustomerResponse> expectedPage = new PageImpl<>(List.of(new CustomerResponse()));
        when(customerService.getAllCustomers(pageable)).thenReturn(expectedPage);

        // Act
        ResponseEntity<Page<CustomerResponse>> response = controller.getAllCustomers(pageable);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expectedPage);
    }

    @Test
    @DisplayName("getCustomer - delega tipo y número de documento y responde 200")
    void getCustomer_deberiaDelegarDocumentoYResponder200() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        CustomerResponse expected = new CustomerResponse();
        expected.setId(customerId);
        when(customerService.getCustomer((short) 1, "1000000001")).thenReturn(expected);

        // Act
        ResponseEntity<CustomerResponse> response = controller.getCustomer((short) 1, "1000000001");

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(customerId);
    }

    @Test
    @DisplayName("changeStatus - delega tipo y número de documento y nuevo estado, responde 204")
    void changeStatus_deberiaDelegarDocumentoYEstadoYResponder204() {
        // Arrange
        ChangeCustomerStatusRequest request = new ChangeCustomerStatusRequest();
        request.setDocumentTypeId((short) 1);
        request.setDocumentNumber("1000000001");
        request.setStatus(UserStatusEnum.BLOCKED);

        // Act
        ResponseEntity<Void> response = controller.changeStatus(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(customerService).changeStatus((short) 1, "1000000001", UserStatusEnum.BLOCKED);
    }
}