package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.mapper.AccountMapper;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.shared.exception.BusinessException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotActiveException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccountApiService implements AccountApi {

    private final AccountRepository accountRepository;
    private final AccountLookup accountLookup;
    private final AccountMapper accountMapper;
    private final CustomerApi customerApi;

    public AccountApiService(AccountRepository accountRepository,
                             AccountLookup accountLookup,
                             AccountMapper accountMapper,
                             CustomerApi customerApi) {
        this.accountRepository = accountRepository;
        this.accountLookup = accountLookup;
        this.accountMapper = accountMapper;
        this.customerApi = customerApi;
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
        AccountView view = accountMapper.toView(accountLookup.byId(accountId));
        if (!view.isActive()) {
            throw new AccountNotActiveException("La cuenta no está activa: " + accountId);
        }
        return view;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountView requireOwnedAccount(UUID userId, String accountNumber) {
        CustomerView customer = customerApi.getCustomerByUserId(userId);
        return accountMapper.toView(accountLookup.byNumberOwnedBy(accountNumber, customer.id()));
    }

    @Override
    @Transactional(readOnly = true)
    public UUID getOwnerUserId(UUID accountId) {
        return customerApi.getCustomer(accountLookup.byId(accountId).getCustomerId()).userId();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Se declara {@code noRollbackFor = BusinessException.class} porque el rechazo es un resultado esperado que el
     * llamante atrapa para registrar la transferencia como {@code REJECTED}. Sin esa opción, la excepción marcaría la
     * transacción del llamante como "solo revertir" y ese registro se perdería.
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public void applyTransfer(UUID senderAccountId, UUID receiverAccountId, BigDecimal amount) {
        if (senderAccountId.equals(receiverAccountId)) {
            throw new IllegalArgumentException("Una transferencia necesita dos cuentas distintas");
        }
        Map<UUID, Account> locked = accountRepository.findAllByIdForUpdate(List.of(senderAccountId, receiverAccountId))
                .stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        Account sender = require(locked, senderAccountId);
        Account receiver = require(locked, receiverAccountId);

        sender.requireCanDebit(amount);
        receiver.requireCanCredit(amount);
        sender.debit(amount);
        receiver.credit(amount);
        accountRepository.saveAll(List.of(sender, receiver));
    }

    @Override
    @Transactional
    public AccountView applyDeposit(UUID receiverAccountId, BigDecimal amount) {
        Account receiver = require(accountRepository.findAllByIdForUpdate(List.of(receiverAccountId)).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity())), receiverAccountId);
        receiver.credit(amount);
        return accountMapper.toView(accountRepository.save(receiver));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Es la operación de los retiros de HU19. Todavía no la llama nadie: existe antes que el flujo de retiro, que la
     * usará al confirmar el retiro, igual que la transferencia usa {@link #applyTransfer}.
     *
     * <p>Se declara {@code noRollbackFor = BusinessException.class}, como {@link #applyTransfer} y no como
     * {@link #applyDeposit}, porque el rechazo (saldo insuficiente, cuenta no activa o inexistente) es un resultado
     * esperado que el llamante atrapa para registrar el retiro como {@code REJECTED}. Sin esa opción, la excepción
     * marcaría la transacción del llamante como "solo revertir" y ese registro se perdería. Un monto no positivo sí la
     * marca, porque es un error de programación y no un rechazo.
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public AccountView applyWithdrawal(UUID senderAccountId, BigDecimal amount) {
        Account sender = require(accountRepository.findAllByIdForUpdate(List.of(senderAccountId)).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity())), senderAccountId);
        sender.debit(amount);
        return accountMapper.toView(accountRepository.save(sender));
    }

    // Cuenta de las bloqueadas, o AccountNotFoundException si la consulta no la devuelve
    private static Account require(Map<UUID, Account> accounts, UUID accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new AccountNotFoundException("Cuenta no encontrada: " + accountId);
        }
        return account;
    }
}
