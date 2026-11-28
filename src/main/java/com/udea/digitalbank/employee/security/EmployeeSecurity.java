package com.udea.digitalbank.employee.security;

import com.udea.digitalbank.shared.security.Roles;
import com.udea.digitalbank.shared.security.SecurityModule;
import org.springframework.stereotype.Component;

import java.util.List;

/** /api/v1/employees/** es solo de administradores. */
@Component
public class EmployeeSecurity implements SecurityModule {

    @Override
    public List<RoleRoute> roleRoutes() {
        return List.of(RoleRoute.any("/api/v1/employees/**", Roles.ADMIN));
    }
}
