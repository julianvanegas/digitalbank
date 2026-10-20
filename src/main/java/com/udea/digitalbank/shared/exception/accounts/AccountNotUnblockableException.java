package com.udea.digitalbank.shared.exception.accounts;

import com.udea.digitalbank.shared.exception.BusinessException;

/**
 * La cuenta no se puede desbloquear porque no está bloqueada: desbloquear solo tiene sentido
 * desde el estado BLOCKED, y una cuenta inactiva o cerrada no debe quedar activa por esta vía.
 */
public class AccountNotUnblockableException extends BusinessException {
    public AccountNotUnblockableException(String message) {
        super(Kind.CONFLICT, message);
    }
}
