package com.udea.digitalbank.shared.exception.transactions;

import com.udea.digitalbank.shared.exception.BusinessException;

/**
 * El código de verificación es incorrecto pero quedan intentos: la transferencia sigue {@code PENDING}.
 * Responde 400.
 */
public class InvalidTransactionCodeException extends BusinessException {
    public InvalidTransactionCodeException(String message) {
        super(Kind.INVALID, message);
    }
}
