package com.udea.digitalbank.accounts.security;

import com.udea.digitalbank.shared.security.Roles;
import com.udea.digitalbank.shared.security.SecurityModule;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Quién puede usar cada ruta de cuentas. El orden importa: gana la primera que
 * coincide, así que /by-customer, que es solo de administradores, va antes que /{accountNumber}.
 */
@Component
public class AccountSecurity implements SecurityModule {

    private static final String BASE = "/api/v1/accounts";

    @Override
    public List<RoleRoute> roleRoutes() {
        return List.of(
                RoleRoute.post(BASE, Roles.CUSTOMER, Roles.ADMIN),
                RoleRoute.get(BASE, Roles.CUSTOMER),
                RoleRoute.get(BASE + "/by-customer", Roles.ADMIN),
                RoleRoute.get(BASE + "/by-customer/balances", Roles.ADMIN),
                RoleRoute.get(BASE + "/*", Roles.CUSTOMER, Roles.ADMIN),
                RoleRoute.get(BASE + "/*/balance", Roles.CUSTOMER, Roles.ADMIN),
                RoleRoute.patch(BASE + "/*/block", Roles.ADMIN),
                RoleRoute.patch(BASE + "/*/unlock", Roles.ADMIN),
                RoleRoute.patch(BASE + "/*/close", Roles.CUSTOMER, Roles.ADMIN));
    }
}
