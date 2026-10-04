package com.udea.digitalbank.shared.exception.employee;

import com.udea.digitalbank.shared.exception.BusinessException;

public class EmployeeNotActiveException extends BusinessException {
    public EmployeeNotActiveException(String message) {
        super(Kind.FORBIDDEN, message);
    }
}
