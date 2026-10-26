package com.udea.digitalbank.customer.service;

import com.udea.digitalbank.auth.api.UserApi;
import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.customer.domain.Customer;
import com.udea.digitalbank.customer.dto.CustomerResponse;
import com.udea.digitalbank.customer.mapper.CustomerMapper;
import com.udea.digitalbank.customer.repository.CustomerRepository;
import com.udea.digitalbank.customer.repository.DocumentTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueba unitaria (AAA) de {@link CustomerService#getProfile}: consultar el perfil no modifica ningún dato
 * (HU06 CA04). El mapper es el real generado por MapStruct.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU06 - Consulta de perfil sin efectos")
class CustomerProfileQueryTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private DocumentTypeRepository documentTypeRepository;
    @Mock
    private UserApi userApi;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        CustomerMapper mapper = Mappers.getMapper(CustomerMapper.class);
        customerService = new CustomerService(customerRepository, documentTypeRepository, userApi, mapper,
                new CustomerLookup(customerRepository, userApi));
    }

    @Test
    @DisplayName("HU06 CA04 - Consultar el perfil dos veces devuelve los mismos datos y no guarda ni cambia nada")
    void noDeberiaModificarElPerfilAlConsultar() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setUserId(userId);
        customer.setFirstNames("Maria Fernanda");
        customer.setLastNames("Lopez Gomez");
        customer.setDocumentNumber("1032456123");
        customer.setPhone("+573001112233");
        customer.setBirthDate(LocalDate.of(1990, 5, 14));
        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(customer));
        when(userApi.getUser(userId)).thenReturn(new UserView(userId, "maria.lopez@example.com", "CUSTOMER", "ACTIVE"));

        // Act
        CustomerResponse first = customerService.getProfile(userId);
        CustomerResponse second = customerService.getProfile(userId);

        // Assert
        assertThat(second).isEqualTo(first);
        assertThat(first.getPhone()).isEqualTo("+573001112233");
        assertThat(first.getStatus()).isEqualTo("ACTIVE");
        verify(customerRepository, never()).save(any());
        verify(userApi, never()).changeStatus(any(), any());
    }
}
