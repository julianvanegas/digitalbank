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

    /**
     * Busca por tipo y número de documento juntos: el mismo número puede pertenecer a otra persona con
     * otro tipo de documento. Lanza CustomerNotFoundException si no existe.
     * El mensaje de no encontrado incluye el documento consultado; está pensado para un endpoint de ADMIN,
     * que es quien lo envía. Si alguna vez se expone esta búsqueda a un cliente, se debe revisar.
     */
    CustomerView getCustomerByDocument(Short documentTypeId, String documentNumber);

    /** En una sola consulta, para listados: los ids inexistentes se omiten. */
    List<CustomerView> getCustomers(Collection<UUID> customerIds);

    /** Lanza CustomerNotFoundException si no existe o CustomerNotActiveException si no está ACTIVE. */
    CustomerView requireActiveCustomer(UUID customerId);
}
