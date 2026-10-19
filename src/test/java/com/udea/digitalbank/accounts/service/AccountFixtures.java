package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountTypeEnum;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.domain.AccountStatus;
import com.udea.digitalbank.accounts.domain.AccountType;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

/** Datos de prueba compartidos por los tests de los servicios de cuentas. */
final class AccountFixtures {

    static final String ACCOUNT_NUMBER = "100000000001";

    private AccountFixtures() {
    }

    static AccountStatus status(AccountStatusEnum statusEnum) {
        AccountStatus status = new AccountStatus();
        ReflectionTestUtils.setField(status, "id", (short) (statusEnum.ordinal() + 1));
        ReflectionTestUtils.setField(status, "code", statusEnum.name());
        return status;
    }

    static AccountType type(AccountTypeEnum typeEnum) {
        AccountType type = new AccountType();
        ReflectionTestUtils.setField(type, "id", (short) (typeEnum.ordinal() + 1));
        ReflectionTestUtils.setField(type, "code", typeEnum.name());
        return type;
    }

    static Account buildAccount(AccountStatusEnum statusEnum) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(account, "customerId", UUID.randomUUID());
        ReflectionTestUtils.setField(account, "accountIndex", (short) 1);
        ReflectionTestUtils.setField(account, "accountNumber", ACCOUNT_NUMBER);
        ReflectionTestUtils.setField(account, "status", status(statusEnum));
        ReflectionTestUtils.setField(account, "balance", new BigDecimal("150.75"));
        return account;
    }
}
