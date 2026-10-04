package com.udea.digitalbank.employee.service;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.employee.api.EmployeeApi;
import com.udea.digitalbank.employee.api.EmployeeView;
import com.udea.digitalbank.employee.domain.Employee;
import com.udea.digitalbank.employee.mapper.EmployeeMapper;
import com.udea.digitalbank.employee.repository.EmployeeRepository;
import com.udea.digitalbank.shared.exception.employee.EmployeeNotActiveException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EmployeeApiService implements EmployeeApi {

    private final EmployeeRepository employeeRepository;
    private final EmployeeLookup employeeLookup;
    private final EmployeeMapper employeeMapper;

    public EmployeeApiService(EmployeeRepository employeeRepository,
                              EmployeeLookup employeeLookup,
                              EmployeeMapper employeeMapper) {
        this.employeeRepository = employeeRepository;
        this.employeeLookup = employeeLookup;
        this.employeeMapper = employeeMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeView getEmployee(UUID employeeId) {
        return toView(employeeLookup.byId(employeeId));
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeView getEmployeeByUserId(UUID userId) {
        return toView(employeeLookup.byUserId(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeView> getEmployees(Collection<UUID> employeeIds) {
        List<Employee> employees = employeeRepository.findAllById(employeeIds);
        Map<UUID, UserView> users = employeeLookup.usersOf(employees);
        return employees.stream().map(e -> employeeMapper.toView(e, users.get(e.getUserId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeView requireActiveEmployee(UUID employeeId) {
        EmployeeView view = getEmployee(employeeId);
        if (!UserStatusEnum.ACTIVE.name().equals(view.status())) {
            throw new EmployeeNotActiveException("El empleado no está activo: " + employeeId);
        }
        return view;
    }

    private EmployeeView toView(Employee employee) {
        return employeeMapper.toView(employee, employeeLookup.userOf(employee));
    }
}
