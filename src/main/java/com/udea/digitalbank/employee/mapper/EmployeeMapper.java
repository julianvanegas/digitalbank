package com.udea.digitalbank.employee.mapper;

import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.employee.api.EmployeeView;
import com.udea.digitalbank.employee.domain.Employee;
import com.udea.digitalbank.employee.dto.EmployeeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EmployeeMapper {

    // Datos del perfil (Employee) más los del usuario (UserView, que llega por UserApi)
    @Mapping(target = "id", source = "employee.id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "role", source = "user.role")
    @Mapping(target = "status", source = "user.status")
    EmployeeResponse toResponse(Employee employee, UserView user);

    // Vista para otros módulos (EmployeeApi)
    @Mapping(target = "id", source = "employee.id")
    @Mapping(target = "userId", source = "employee.userId")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "status", source = "user.status")
    EmployeeView toView(Employee employee, UserView user);
}
