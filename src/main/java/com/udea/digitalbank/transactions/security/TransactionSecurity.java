package com.udea.digitalbank.transactions.security;

import com.udea.digitalbank.shared.security.Roles;
import com.udea.digitalbank.shared.security.SecurityModule;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Quién puede usar cada ruta de transacciones. Transferir y confirmar son solo de clientes: la operación es del
 * titular y la autoriza con su propio segundo factor. El comprobante también lo puede ver un administrador.
 */
@Component
public class TransactionSecurity implements SecurityModule {

    private static final String BASE = "/api/v1/transactions";

    @Override
    public List<RoleRoute> roleRoutes() {
        return List.of(
                RoleRoute.post(BASE + "/transfers", Roles.CUSTOMER),
                RoleRoute.post(BASE + "/*/confirm", Roles.CUSTOMER),
                RoleRoute.get(BASE + "/*", Roles.CUSTOMER, Roles.ADMIN));
    }
}
