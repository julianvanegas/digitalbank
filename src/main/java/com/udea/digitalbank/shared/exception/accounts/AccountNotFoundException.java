package com.udea.digitalbank.shared.exception.accounts;

import com.udea.digitalbank.shared.exception.BusinessException;

public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException(String message) {
        super(Kind.NOT_FOUND, message);
    }
}
