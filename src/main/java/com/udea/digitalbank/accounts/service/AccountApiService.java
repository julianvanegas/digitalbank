package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.accounts.mapper.AccountMapper;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.shared.exception.accounts.AccountNotActiveException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AccountApiService implements AccountApi {

    private final AccountRepository accountRepository;
    private final AccountLookup accountLookup;
    private final AccountMapper accountMapper;

    public AccountApiService(AccountRepository accountRepository,
                             AccountLookup accountLookup,
                             AccountMapper accountMapper) {
        this.accountRepository = accountRepository;
        this.accountLookup = accountLookup;
        this.accountMapper = accountMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountView getAccount(UUID accountId) {
        return accountMapper.toView(accountLookup.byId(accountId));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountView getAccountByNumber(String accountNumber) {
        return accountMapper.toView(accountLookup.byNumber(accountNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountView> getAccountsOfCustomer(UUID customerId) {
        return accountRepository.findByCustomerIdOrderByCreatedAt(customerId).stream()
                .map(accountMapper::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountView requireActiveAccount(UUID accountId) {
        AccountView view = getAccount(accountId);
        if (!AccountStatusEnum.ACTIVE.name().equals(view.status())) {
            throw new AccountNotActiveException("La cuenta no está activa: " + accountId);
        }
        return view;
    }
}
