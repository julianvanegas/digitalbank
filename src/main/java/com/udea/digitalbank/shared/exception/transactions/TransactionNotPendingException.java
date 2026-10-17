package com.udea.digitalbank.shared.exception.transactions;

import com.udea.digitalbank.shared.exception.BusinessException;

public class TransactionNotPendingException extends BusinessException {
    public TransactionNotPendingException(String message) {
        super(Kind.CONFLICT, message);
    }
}
