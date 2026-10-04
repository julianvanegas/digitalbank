package com.udea.digitalbank.employee.api;

import java.util.UUID;

// Vista de solo lectura de un empleado para otros módulos
public record EmployeeView(UUID id, UUID userId, String firstNames, String lastNames,
                           String email, String status) {
}
