package com.udea.digitalbank.shared.exception.auth;

import com.udea.digitalbank.shared.exception.BusinessException;

public class InvalidCredentialsException extends BusinessException {
    public InvalidCredentialsException(String message) {
        super(Kind.UNAUTHENTICATED, message);
    }
}
