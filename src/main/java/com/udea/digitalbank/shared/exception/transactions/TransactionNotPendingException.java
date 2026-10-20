package com.udea.digitalbank.shared.exception.transactions;

import com.udea.digitalbank.shared.exception.BusinessException;

/**
 * La operación intenta cambiar o confirmar una transacción que ya no está {@code PENDING} (por ejemplo, confirmar una
 * cancelada). Responde 409.
 */
public class TransactionNotPendingException extends BusinessException {
    public TransactionNotPendingException(String message) {
        super(Kind.CONFLICT, message);
    }
}
