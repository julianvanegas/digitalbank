package com.udea.digitalbank.shared.exception.accounts;

import com.udea.digitalbank.shared.exception.BusinessException;

/**
 * La cuenta no se puede cerrar por su situación: ya está cerrada, está bloqueada (primero hay que
 * desbloquearla) o todavía tiene saldo.
 */
public class AccountNotClosableException extends BusinessException {
    public AccountNotClosableException(String message) {
        super(Kind.CONFLICT, message);
    }
}
