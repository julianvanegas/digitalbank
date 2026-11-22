package com.udea.digitalbank.shared.exception.transactions;

import com.udea.digitalbank.shared.exception.BusinessException;

/**
 * La operación supera un tope transaccional del cliente (por operación, cantidad diaria o acumulado diario). Responde
 * 409, igual que los fondos insuficientes: la solicitud es válida, pero el estado de la cuenta no la admite hoy.
 */
public class TransactionLimitExceededException extends BusinessException {
    public TransactionLimitExceededException(String message) {
        super(Kind.CONFLICT, message);
    }
}
