package com.udea.digitalbank.shared.exception.transactions;

import com.udea.digitalbank.shared.exception.BusinessException;

public class InvalidReceiverAccountException extends BusinessException {
    public InvalidReceiverAccountException(String message) {
        super(Kind.INVALID, message);
    }
}
