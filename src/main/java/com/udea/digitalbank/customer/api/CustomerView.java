package com.udea.digitalbank.customer.api;

import java.util.UUID;

// Vista de solo lectura de un cliente para otros módulos (sin teléfono ni fecha de nacimiento)
public record CustomerView(UUID id, UUID userId, String firstNames, String lastNames,
                           String documentType, String documentNumber, String email, String status) {
}
