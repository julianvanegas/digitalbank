package com.udea.digitalbank.shared.exception.accounts;

import com.udea.digitalbank.shared.exception.BusinessException;

public class AccountNotActiveException extends BusinessException {
    public AccountNotActiveException(String message) {
        super(Kind.FORBIDDEN, message);
    }
}
