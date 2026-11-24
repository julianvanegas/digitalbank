package com.udea.digitalbank.shared.exception.transactions;

import com.udea.digitalbank.shared.exception.BusinessException;

/**
 * Se consultan los topes de un tipo de operación (o de cuenta) que el sistema no contempla. Responde 404: no existe
 * el recurso pedido.
 */
public class LimitsNotDefinedException extends BusinessException {
    public LimitsNotDefinedException(String message) {
        super(Kind.NOT_FOUND, message);
    }
}
