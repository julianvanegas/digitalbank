package com.udea.digitalbank.shared.exception.transactions;

import com.udea.digitalbank.shared.exception.BusinessException;

public class TransactionNotFoundException extends BusinessException {
    public TransactionNotFoundException(String message) {
        super(Kind.NOT_FOUND, message);
    }
}
