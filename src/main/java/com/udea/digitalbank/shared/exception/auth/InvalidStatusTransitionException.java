package com.udea.digitalbank.shared.exception.auth;

import com.udea.digitalbank.shared.exception.BusinessException;

/** Un administrador pidió un cambio de estado de usuario que la matriz de transiciones no permite. */
public class InvalidStatusTransitionException extends BusinessException {
    public InvalidStatusTransitionException(String message) {
        super(Kind.CONFLICT, message);
    }
}
