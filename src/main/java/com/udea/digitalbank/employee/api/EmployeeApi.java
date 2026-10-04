package com.udea.digitalbank.employee.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Única puerta de entrada de otros módulos a employee (p. ej. reviews).
 * Los módulos solo guardan el id del empleado; nunca importan entidades ni repositorios de employee.
 * La implementación vive en employee.service (EmployeeApiService).
 */
public interface EmployeeApi {

    /** Lanza EmployeeNotFoundException si el empleado no existe. */
    EmployeeView getEmployee(UUID employeeId);

    /** Lanza EmployeeNotFoundException si el usuario no tiene perfil de empleado. */
    EmployeeView getEmployeeByUserId(UUID userId);

    /** En una sola consulta, para listados: los ids inexistentes se omiten. */
    List<EmployeeView> getEmployees(Collection<UUID> employeeIds);

    /** Lanza EmployeeNotFoundException si no existe o EmployeeNotActiveException si no está ACTIVE. */
    EmployeeView requireActiveEmployee(UUID employeeId);
}
