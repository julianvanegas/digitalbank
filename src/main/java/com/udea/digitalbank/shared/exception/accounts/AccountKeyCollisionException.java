package com.udea.digitalbank.shared.exception.accounts;

import com.udea.digitalbank.shared.exception.BusinessException;

/**
 * Dos clientes distintos produjeron la misma llave. Ocurre cuando coinciden las dos primeras letras
 * del primer nombre y los últimos dígitos del documento: la estructura de CA01 es determinista, así
 * que no se puede reintentar con otro valor. Necesita intervención manual.
 */
public class AccountKeyCollisionException extends BusinessException {
    public AccountKeyCollisionException(String message) {
        super(Kind.CONFLICT, message);
    }
}
