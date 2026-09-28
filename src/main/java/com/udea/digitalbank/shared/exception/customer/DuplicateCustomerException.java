package com.udea.digitalbank.shared.exception.customer;

import com.udea.digitalbank.shared.exception.BusinessException;

public class DuplicateCustomerException extends BusinessException {
    public DuplicateCustomerException(String message) {
        super(Kind.CONFLICT, message);
    }
}
