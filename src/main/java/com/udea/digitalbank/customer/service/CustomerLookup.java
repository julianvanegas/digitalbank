package com.udea.digitalbank.customer.service;

import com.udea.digitalbank.auth.api.UserApi;
import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.customer.domain.Customer;
import com.udea.digitalbank.customer.repository.CustomerRepository;
import com.udea.digitalbank.shared.exception.customer.CustomerNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Búsquedas de clientes compartidas por CustomerService (uso interno) y CustomerApiService (contrato
 * para otros módulos): así el mensaje de "no encontrado" y la resolución de usuarios viven en un solo lugar.
 */
@Component
class CustomerLookup {

    private final CustomerRepository customerRepository;
    private final UserApi userApi;

    CustomerLookup(CustomerRepository customerRepository, UserApi userApi) {
        this.customerRepository = customerRepository;
        this.userApi = userApi;
    }

    Customer byId(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Cliente no encontrado: " + customerId));
    }

    Customer byUserId(UUID userId) {
        return customerRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomerNotFoundException("Cliente no encontrado para el usuario: " + userId));
    }

    UserView userOf(Customer customer) {
        return userApi.getUser(customer.getUserId());
    }

    // Una sola consulta a auth para todo el listado, indexada por id de usuario
    Map<UUID, UserView> usersOf(Collection<Customer> customers) {
        return userApi.getUsers(customers.stream().map(Customer::getUserId).toList())
                .stream().collect(Collectors.toMap(UserView::id, Function.identity()));
    }
}
