package com.udea.digitalbank.shared.exception.transactions;

import com.udea.digitalbank.shared.exception.BusinessException;

/**
 * La solicitud pide transferir a la misma cuenta de origen. Responde 400.
 */
public class SameAccountTransferException extends BusinessException {
    public SameAccountTransferException(String message) {
        super(Kind.INVALID, message);
    }
}
