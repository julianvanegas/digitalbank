package com.udea.digitalbank.shared.exception.transactions;

import com.udea.digitalbank.shared.exception.BusinessException;

public class InvalidTransactionCodeException extends BusinessException {
    public InvalidTransactionCodeException(String message) {
        super(Kind.INVALID, message);
    }
}
