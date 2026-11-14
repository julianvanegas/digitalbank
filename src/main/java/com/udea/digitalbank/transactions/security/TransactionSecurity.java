package com.udea.digitalbank.transactions.security;

import com.udea.digitalbank.shared.security.Roles;
import com.udea.digitalbank.shared.security.SecurityModule;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Permisos por rol de las rutas de transacciones. Iniciar y confirmar una transferencia lo hacen los clientes, que son
 * titulares de la operación, y los administradores, que operan en nombre de un cliente: el código de segundo factor
 * siempre va al titular de la cuenta de origen. La consignación es exclusiva de los administradores. El comprobante y
 * los topes los consulta cualquier usuario autenticado.
 */
@Component
public class TransactionSecurity implements SecurityModule {

    /** Ruta base del módulo. */
    private static final String BASE = "/api/v1/transactions";

    /**
     * Rutas y roles autorizados. Las reglas no se solapan (cambian el método o el patrón), así que el orden no
     * altera el resultado, con una excepción: {@code GET /limits} va antes de {@code GET /*}, que de otro modo lo
     * tomaría por la referencia de una transacción. Toda ruta que no esté aquí se deniega.
     */
    @Override
    public List<RoleRoute> roleRoutes() {
        return List.of(
                RoleRoute.post(BASE + "/transfers", Roles.CUSTOMER, Roles.ADMIN),
                RoleRoute.post(BASE + "/*/confirm", Roles.CUSTOMER, Roles.ADMIN),
                RoleRoute.post(BASE + "/deposits", Roles.ADMIN),
                RoleRoute.get(BASE + "/limits", Roles.CUSTOMER, Roles.ADMIN),
                RoleRoute.get(BASE + "/*", Roles.CUSTOMER, Roles.ADMIN));
    }
}
