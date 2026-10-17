package com.udea.digitalbank.shared.exception.transactions;

import com.udea.digitalbank.shared.exception.BusinessException;

/**
 * El código de verificación ha vencido. La transferencia queda {@code REJECTED} y se debe iniciar de nuevo.
 * Responde 409.
 */
public class TransactionCodeExpiredException extends BusinessException {
    public TransactionCodeExpiredException(String message) {
        super(Kind.CONFLICT, message);
    }
}
