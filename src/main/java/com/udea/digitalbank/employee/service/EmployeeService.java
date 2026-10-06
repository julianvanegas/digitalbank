package com.udea.digitalbank.employee.service;

import com.udea.digitalbank.auth.api.RoleEnum;
import com.udea.digitalbank.auth.api.UserApi;
import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.employee.domain.Employee;
import com.udea.digitalbank.employee.dto.CreateEmployeeRequest;
import com.udea.digitalbank.employee.dto.EmployeeResponse;
import com.udea.digitalbank.employee.mapper.EmployeeMapper;
import com.udea.digitalbank.employee.repository.EmployeeRepository;
import com.udea.digitalbank.shared.exception.employee.OwnStatusChangeException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserApi userApi;
    private final EmployeeMapper employeeMapper;
    private final EmployeeLookup employeeLookup;

    public EmployeeService(EmployeeRepository employeeRepository,
                           UserApi userApi,
                           EmployeeMapper employeeMapper,
                           EmployeeLookup employeeLookup) {
        this.employeeRepository = employeeRepository;
        this.userApi = userApi;
        this.employeeMapper = employeeMapper;
        this.employeeLookup = employeeLookup;
    }

    // Usuario y perfil en una sola transacción: si algo falla no quedan usuarios sin perfil ni al revés.
    // La contraseña inicial es aleatoria y no se entrega a nadie: el empleado confirma su correo con el
    // código que recibe y define la suya en ese mismo paso (verify-email); queda ACTIVE al verificar.
    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        // Valida el email único, guarda el hash y deja al usuario en PENDING_VERIFICATION
        UserView user = userApi.createUser(request.getEmail(), RoleEnum.ADMIN);

        Employee employee = new Employee();
        employee.setUserId(user.id());
        employee.setFirstNames(request.getFirstNames());
        employee.setLastNames(request.getLastNames());

        return employeeMapper.toResponse(employeeRepository.save(employee), user);
    }

    // Perfil propio: se identifica por el id del usuario que viaja en el JWT
    @Transactional(readOnly = true)
    public EmployeeResponse getProfile(UUID userId) {
        return toResponse(employeeLookup.byUserId(userId));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployee(UUID employeeId) {
        return toResponse(employeeLookup.byId(employeeId));
    }

    @Transactional
    public EmployeeResponse updateProfile(UUID userId, String firstNames, String lastNames) {
        Employee employee = employeeLookup.byUserId(userId);
        // solo campos de perfil editables — nunca rol ni estado desde aquí
        if (firstNames != null) {
            employee.setFirstNames(firstNames);
        }
        if (lastNames != null) {
            employee.setLastNames(lastNames);
        }
        return toResponse(employeeRepository.save(employee));
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> getAllEmployees(Pageable pageable) {
        Page<Employee> employees = employeeRepository.findAll(pageable);
        Map<UUID, UserView> users = employeeLookup.usersOf(employees.getContent());
        return employees.map(e -> employeeMapper.toResponse(e, users.get(e.getUserId())));
    }

    // Solo se debe llamar desde un endpoint protegido con @PreAuthorize("hasRole('ADMIN')").
    // Un administrador no cambia su propio estado: evita que se bloquee o inactive a sí mismo.
    // auth valida que el estado exista en el catálogo user_status
    @Transactional
    public void changeStatus(UUID employeeId, UserStatusEnum statusEnum, UUID actingUserId) {
        Employee employee = employeeLookup.byId(employeeId);
        if (employee.getUserId().equals(actingUserId)) {
            throw new OwnStatusChangeException("No puedes cambiar tu propio estado");
        }
        userApi.changeStatus(employee.getUserId(), statusEnum);
    }

    private EmployeeResponse toResponse(Employee employee) {
        return employeeMapper.toResponse(employee, employeeLookup.userOf(employee));
    }
}
