package com.udea.digitalbank.shared.exception.transactions;

import com.udea.digitalbank.shared.exception.BusinessException;

public class TransactionCodeExpiredException extends BusinessException {
    public TransactionCodeExpiredException(String message) {
        super(Kind.CONFLICT, message);
    }
}
