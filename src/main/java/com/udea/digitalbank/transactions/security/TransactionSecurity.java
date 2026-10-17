package com.udea.digitalbank.transactions.security;

import com.udea.digitalbank.shared.security.Roles;
import com.udea.digitalbank.shared.security.SecurityModule;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Permisos por rol de las rutas de transacciones. Iniciar y confirmar una transferencia son exclusivos de los
 * clientes: la operación es del titular y la autoriza con su propio segundo factor. El comprobante también puede
 * consultarlo un administrador.
 */
@Component
public class TransactionSecurity implements SecurityModule {

    /** Ruta base del módulo. */
    private static final String BASE = "/api/v1/transactions";

    /**
     * Rutas y roles autorizados. Las reglas no se solapan (cambian el método o el patrón), así que el orden no
     * altera el resultado. Toda ruta que no esté aquí se deniega.
     */
    @Override
    public List<RoleRoute> roleRoutes() {
        return List.of(
                RoleRoute.post(BASE + "/transfers", Roles.CUSTOMER),
                RoleRoute.post(BASE + "/*/confirm", Roles.CUSTOMER),
                RoleRoute.get(BASE + "/*", Roles.CUSTOMER, Roles.ADMIN));
    }
}
