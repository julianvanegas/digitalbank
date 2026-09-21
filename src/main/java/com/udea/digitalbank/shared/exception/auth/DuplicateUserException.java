package com.udea.digitalbank.shared.exception.auth;

import com.udea.digitalbank.shared.exception.BusinessException;

public class DuplicateUserException extends BusinessException {
    public DuplicateUserException(String message) {
        super(Kind.CONFLICT, message);
    }
}
