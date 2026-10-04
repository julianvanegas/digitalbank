package com.udea.digitalbank.shared.exception.employee;

import com.udea.digitalbank.shared.exception.BusinessException;

public class OwnStatusChangeException extends BusinessException {
    public OwnStatusChangeException(String message) {
        super(Kind.FORBIDDEN, message);
    }
}
