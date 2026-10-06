package com.udea.digitalbank.shared.exception.accounts;

import com.udea.digitalbank.shared.exception.BusinessException;

// La apertura choca con las cuentas que el cliente ya tiene: es un conflicto, no un dato inválido
public class AccountLimitReachedException extends BusinessException {
    public AccountLimitReachedException(String message) {
        super(Kind.CONFLICT, message);
    }
}
