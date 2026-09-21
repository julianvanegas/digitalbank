package com.udea.digitalbank.customer.security;

import com.udea.digitalbank.shared.security.SecurityModule;
import org.springframework.stereotype.Component;

import java.util.List;

// El resto de /api/customers/** exige autenticación y cada endpoint se protege con @PreAuthorize
@Component
public class CustomerSecurity implements SecurityModule {

    @Override
    public List<PublicRoute> publicRoutes() {
        return List.of(PublicRoute.post("/api/customers"));
    }
}
