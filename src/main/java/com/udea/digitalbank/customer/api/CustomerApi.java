package com.udea.digitalbank.customer.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Única puerta de entrada de otros módulos a customer (accounts, transactions).
 * Los módulos solo guardan el id del cliente; nunca importan entidades ni repositorios de customer.
 * La implementación vive en customer.service (CustomerApiService).
 */
public interface CustomerApi {

    /** Lanza CustomerNotFoundException si el cliente no existe. */
    CustomerView getCustomer(UUID customerId);

    /** Lanza CustomerNotFoundException si el usuario no tiene perfil de cliente. */
    CustomerView getCustomerByUserId(UUID userId);

    /** En una sola consulta, para listados: los ids inexistentes se omiten. */
    List<CustomerView> getCustomers(Collection<UUID> customerIds);

    /** Lanza CustomerNotFoundException si no existe o CustomerNotActiveException si no está ACTIVE. */
    CustomerView requireActiveCustomer(UUID customerId);
}
