package com.udea.digitalbank.shared.exception.accounts;

import com.udea.digitalbank.shared.exception.BusinessException;

public class InsufficientFundsException extends BusinessException {
    public InsufficientFundsException(String message) {
        super(Kind.CONFLICT, message);
    }
}
