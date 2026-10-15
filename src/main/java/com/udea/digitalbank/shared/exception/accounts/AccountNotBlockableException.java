package com.udea.digitalbank.shared.exception.accounts;

import com.udea.digitalbank.shared.exception.BusinessException;

/**
 * La cuenta no se puede bloquear por el estado en que se encuentra: o ya está bloqueada (CA02),
 * o no está activa, que es la única condición desde la que CA01 permite el bloqueo.
 */
public class AccountNotBlockableException extends BusinessException {
    public AccountNotBlockableException(String message) {
        super(Kind.CONFLICT, message);
    }
}
