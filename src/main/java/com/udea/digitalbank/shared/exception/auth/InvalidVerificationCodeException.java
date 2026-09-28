package com.udea.digitalbank.shared.exception.auth;

import com.udea.digitalbank.shared.exception.BusinessException;

public class InvalidVerificationCodeException extends BusinessException {
    public InvalidVerificationCodeException(String message) {
        super(Kind.INVALID, message);
    }
}
