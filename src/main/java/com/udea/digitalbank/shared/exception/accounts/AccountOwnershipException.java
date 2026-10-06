package com.udea.digitalbank.shared.exception.accounts;

import com.udea.digitalbank.shared.exception.BusinessException;

// Se sabe quién pide la operación, pero la cuenta o el titular no son suyos
public class AccountOwnershipException extends BusinessException {
    public AccountOwnershipException(String message) {
        super(Kind.FORBIDDEN, message);
    }
}
