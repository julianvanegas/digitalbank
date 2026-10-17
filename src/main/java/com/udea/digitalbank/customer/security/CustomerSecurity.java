package com.udea.digitalbank.customer.security;

import com.udea.digitalbank.shared.security.Roles;
import com.udea.digitalbank.shared.security.SecurityModule;
import org.springframework.stereotype.Component;

import java.util.List;

// El registro y los tipos de documento son públicos; el resto de /api/v1/customers/** se restringe por rol
@Component
public class CustomerSecurity implements SecurityModule {

    @Override
    public List<PublicRoute> publicRoutes() {
        return List.of(
                PublicRoute.post("/api/v1/customers"),
                // Quien se registra aún no tiene token y necesita los ids de los tipos de documento
                PublicRoute.get("/api/v1/customers/document-types"));
    }

    @Override
    public List<RoleRoute> roleRoutes() {
        return List.of(
                RoleRoute.get("/api/v1/customers/profile", Roles.CUSTOMER),
                RoleRoute.put("/api/v1/customers/profile", Roles.CUSTOMER),
                RoleRoute.get("/api/v1/customers", Roles.ADMIN),
                RoleRoute.get("/api/v1/customers/by-document", Roles.ADMIN),
                RoleRoute.patch("/api/v1/customers/status", Roles.ADMIN));
    }
}
