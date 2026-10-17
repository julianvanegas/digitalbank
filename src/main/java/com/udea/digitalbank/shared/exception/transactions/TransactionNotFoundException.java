package com.udea.digitalbank.shared.exception.transactions;

import com.udea.digitalbank.shared.exception.BusinessException;

/**
 * La transacción no existe o pertenece a otro usuario. Ambos casos responden igual (404) para no revelar
 * que la referencia existe.
 */
public class TransactionNotFoundException extends BusinessException {
    public TransactionNotFoundException(String message) {
        super(Kind.NOT_FOUND, message);
    }
}
