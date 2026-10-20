package com.udea.digitalbank.shared.exception.accounts;

import com.udea.digitalbank.shared.exception.BusinessException;

/**
 * El saldo de la cuenta no alcanza para el monto pedido. Responde 409.
 */
public class InsufficientFundsException extends BusinessException {
    public InsufficientFundsException(String message) {
        super(Kind.CONFLICT, message);
    }
}
