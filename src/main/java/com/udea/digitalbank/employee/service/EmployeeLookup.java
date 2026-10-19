package com.udea.digitalbank.employee.service;

import com.udea.digitalbank.auth.api.UserApi;
import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.employee.domain.Employee;
import com.udea.digitalbank.employee.repository.EmployeeRepository;
import com.udea.digitalbank.shared.exception.employee.EmployeeNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Búsquedas de empleados compartidas por EmployeeService (uso interno) y EmployeeApiService (contrato
 * para otros módulos): así el mensaje de "no encontrado" y la resolución de usuarios viven en un solo lugar.
 */
@Component
class EmployeeLookup {

    private final EmployeeRepository employeeRepository;
    private final UserApi userApi;

    EmployeeLookup(EmployeeRepository employeeRepository, UserApi userApi) {
        this.employeeRepository = employeeRepository;
        this.userApi = userApi;
    }

    Employee byId(UUID employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException("Empleado no encontrado: " + employeeId));
    }

    Employee byUserId(UUID userId) {
        return employeeRepository.findByUserId(userId)
                .orElseThrow(() -> new EmployeeNotFoundException("Empleado no encontrado para el usuario: " + userId));
    }

    UserView userOf(Employee employee) {
        return userApi.getUser(employee.getUserId());
    }

    // Una sola consulta a auth para todo el listado, indexada por id de usuario
    Map<UUID, UserView> usersOf(Collection<Employee> employees) {
        return userApi.getUsers(employees.stream().map(Employee::getUserId).toList())
                .stream().collect(Collectors.toMap(UserView::id, Function.identity()));
    }
}
