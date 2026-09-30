package com.udea.digitalbank.shared.exception.customer;

import com.udea.digitalbank.shared.exception.BusinessException;

public class CustomerNotActiveException extends BusinessException {
    public CustomerNotActiveException(String message) {
        super(Kind.FORBIDDEN, message);
    }
}
