package com.udea.digitalbank.shared.exception.employee;

import com.udea.digitalbank.shared.exception.BusinessException;

public class EmployeeNotFoundException extends BusinessException {
    public EmployeeNotFoundException(String message) {
        super(Kind.NOT_FOUND, message);
    }
}
