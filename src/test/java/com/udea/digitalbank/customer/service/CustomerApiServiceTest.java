package com.udea.digitalbank.customer.service;

import com.udea.digitalbank.auth.api.UserApi;
import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.customer.domain.Customer;
import com.udea.digitalbank.customer.domain.DocumentType;
import com.udea.digitalbank.customer.mapper.CustomerMapper;
import com.udea.digitalbank.customer.repository.CustomerRepository;
import com.udea.digitalbank.shared.exception.customer.CustomerNotActiveException;
import com.udea.digitalbank.shared.exception.customer.CustomerNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerApiService - contrato de customer para accounts y transactions")
class CustomerApiServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private UserApi userApi;

    private CustomerApiService customerApiService;

    // CustomerLookup y CustomerMapper reales sobre los mocks: se prueba el resultado, no la estructura interna
    @BeforeEach
    void setUp() {
        customerApiService = new CustomerApiService(customerRepository,
                new CustomerLookup(customerRepository, userApi), Mappers.getMapper(CustomerMapper.class));
    }

    private final UUID customerId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private Customer buildCustomer() {
        DocumentType documentType = new DocumentType();
        ReflectionTestUtils.setField(documentType, "code", "CC");
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setUserId(userId);
        customer.setFirstNames("Ana");
        customer.setLastNames("Gómez");
        customer.setDocumentType(documentType);
        customer.setDocumentNumber("1000000001");
        return customer;
    }

    private UserView buildUser(String status) {
        return new UserView(userId, "ana@example.com", "CUSTOMER", status);
    }

    @Test
    @DisplayName("getCustomer combina el perfil con el email y estado de auth")
    void getCustomer_returnsView() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(buildCustomer()));
        when(userApi.getUser(userId)).thenReturn(buildUser("ACTIVE"));

        CustomerView view = customerApiService.getCustomer(customerId);

        assertThat(view.id()).isEqualTo(customerId);
        assertThat(view.userId()).isEqualTo(userId);
        assertThat(view.documentType()).isEqualTo("CC");
        assertThat(view.email()).isEqualTo("ana@example.com");
        assertThat(view.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("getCustomer lanza CustomerNotFoundException si no existe")
    void getCustomer_notFound() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerApiService.getCustomer(customerId))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    @DisplayName("getCustomerByUserId resuelve el cliente por el id del usuario")
    void getCustomerByUserId_returnsView() {
        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(buildCustomer()));
        when(userApi.getUser(userId)).thenReturn(buildUser("ACTIVE"));

        assertThat(customerApiService.getCustomerByUserId(userId).id()).isEqualTo(customerId);
    }

    @Test
    @DisplayName("getCustomers resuelve los usuarios en una sola llamada")
    void getCustomers_batch() {
        when(customerRepository.findAllById(List.of(customerId))).thenReturn(List.of(buildCustomer()));
        when(userApi.getUsers(List.of(userId))).thenReturn(List.of(buildUser("ACTIVE")));

        List<CustomerView> views = customerApiService.getCustomers(List.of(customerId));

        assertThat(views).hasSize(1);
        assertThat(views.get(0).email()).isEqualTo("ana@example.com");
    }

    @Test
    @DisplayName("requireActiveCustomer devuelve la vista si el cliente está ACTIVE")
    void requireActiveCustomer_active() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(buildCustomer()));
        when(userApi.getUser(userId)).thenReturn(buildUser("ACTIVE"));

        assertThat(customerApiService.requireActiveCustomer(customerId).id()).isEqualTo(customerId);
    }

    @Test
    @DisplayName("requireActiveCustomer rechaza a un cliente que no está ACTIVE")
    void requireActiveCustomer_notActive() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(buildCustomer()));
        when(userApi.getUser(userId)).thenReturn(buildUser("BLOCKED"));

        assertThatThrownBy(() -> customerApiService.requireActiveCustomer(customerId))
                .isInstanceOf(CustomerNotActiveException.class);
    }
}
