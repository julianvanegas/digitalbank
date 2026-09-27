package com.udea.digitalbank.customer.controller;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.customer.dto.ChangeStatusRequest;
import com.udea.digitalbank.customer.dto.CreateCustomerRequest;
import com.udea.digitalbank.customer.dto.CustomerResponse;
import com.udea.digitalbank.customer.dto.UpdateProfileRequest;
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
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) para CustomerController. Se instancia directamente, sin MockMvc:
 * basta con verificar el código HTTP devuelto y que delega los parámetros correctos al service.
 * El userId "autenticado" se simula devolviendo un Long desde Authentication.getPrincipal(),
 * tal como lo deja JwtAuthenticationFilter en producción.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerController")
class CustomerControllerTest {

    @Mock
    private CustomerService customerService;

    @Mock
    private Authentication authentication;

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
        request.setPassword("Passw0rd!");

        CustomerResponse expected = new CustomerResponse();
        expected.setId(1L);
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
        when(authentication.getPrincipal()).thenReturn(10L);
        CustomerResponse expected = new CustomerResponse();
        expected.setUserId(10L);
        when(customerService.getProfile(10L)).thenReturn(expected);

        // Act
        ResponseEntity<CustomerResponse> response = controller.myProfile(authentication);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUserId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("updateProfile - HU07: delega userId y los 3 campos editables, responde 200")
    void updateProfile_deberiaDelegarCamposYResponder200() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(10L);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFirstNames("Nuevo Nombre");
        request.setLastNames("Nuevo Apellido");
        request.setPhone("+573111111111");

        CustomerResponse expected = new CustomerResponse();
        when(customerService.updateProfile(10L, "Nuevo Nombre", "Nuevo Apellido", "+573111111111"))
                .thenReturn(expected);

        // Act
        ResponseEntity<CustomerResponse> response = controller.updateProfile(authentication, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(customerService).updateProfile(10L, "Nuevo Nombre", "Nuevo Apellido", "+573111111111");
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
    @DisplayName("getCustomer - delega el id y responde 200")
    void getCustomer_deberiaDelegarIdYResponder200() {
        // Arrange
        CustomerResponse expected = new CustomerResponse();
        expected.setId(5L);
        when(customerService.getCustomer(5L)).thenReturn(expected);

        // Act
        ResponseEntity<CustomerResponse> response = controller.getCustomer(5L);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("changeStatus - delega id y nuevo estado, responde 204")
    void changeStatus_deberiaDelegarIdYEstadoYResponder204() {
        // Arrange
        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setStatus(UserStatusEnum.BLOCKED);

        // Act
        ResponseEntity<Void> response = controller.changeStatus(5L, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(customerService).changeStatus(5L, UserStatusEnum.BLOCKED);
    }
}