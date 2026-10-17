package com.udea.digitalbank.shared.exception.transactions;

import com.udea.digitalbank.shared.exception.BusinessException;

public class SameAccountTransferException extends BusinessException {
    public SameAccountTransferException(String message) {
        super(Kind.INVALID, message);
    }
}
