package com.udea.digitalbank.shared.exception.customer;

import com.udea.digitalbank.shared.exception.BusinessException;

public class CustomerNotFoundException extends BusinessException {
    public CustomerNotFoundException(String message) {
        super(Kind.NOT_FOUND, message);
    }
}
