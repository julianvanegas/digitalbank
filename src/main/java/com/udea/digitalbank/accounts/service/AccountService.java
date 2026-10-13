package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.dto.AccountBalanceResponse;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.mapper.AccountMapper;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.shared.exception.accounts.AccountLimitReachedException;
import com.udea.digitalbank.shared.exception.accounts.AccountOwnershipException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    static final int MAX_ACCOUNTS_PER_CUSTOMER = 3;

    private final AccountRepository accountRepository;
    private final CustomerApi customerApi;
    private final AccountMapper accountMapper;
    private final AccountLookup accountLookup;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AccountCatalog accountCatalog;

    public AccountService(AccountRepository accountRepository,
                          CustomerApi customerApi,
                          AccountMapper accountMapper,
                          AccountLookup accountLookup,
                          AccountNumberGenerator accountNumberGenerator,
                          AccountCatalog accountCatalog) {
        this.accountRepository = accountRepository;
        this.customerApi = customerApi;
        this.accountMapper = accountMapper;
        this.accountLookup = accountLookup;
        this.accountNumberGenerator = accountNumberGenerator;
        this.accountCatalog = accountCatalog;
    }

    /**
     * Apertura por el propio cliente. El titular se deduce del id de usuario del token, nunca del
     * cuerpo: si llega un customerId ajeno se rechaza en vez de abrir la cuenta equivocada.
     * No hace falta comprobar que el cliente esté activo: el filtro del JWT ya rechaza
     * cualquier token cuyo usuario no lo esté.
     */
    @Transactional
    public AccountResponse openForSelf(UUID userId, UUID requestedCustomerId) {
        CustomerView customer = customerApi.getCustomerByUserId(userId);
        if (requestedCustomerId != null && !requestedCustomerId.equals(customer.id())) {
            throw new AccountOwnershipException("No puedes abrir una cuenta a nombre de otro cliente");
        }
        return open(customer.id());
    }

    /**
     * Apertura hecha por un administrador en nombre de un cliente.
     * Solo se debe llamar desde un endpoint protegido con @PreAuthorize("hasRole('ADMIN')").
     */
    @Transactional
    public AccountResponse openForCustomer(UUID customerId) {
        // La fachada de customer valida en un solo paso que el cliente exista y esté ACTIVE
        CustomerView customer = customerApi.requireActiveCustomer(customerId);
        return open(customer.id());
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts(UUID userId) {
        CustomerView customer = customerApi.getCustomerByUserId(userId);
        return accountRepository.findByCustomerIdOrderByCreatedAt(customer.id()).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getMyAccount(UUID userId, UUID accountId) {
        CustomerView customer = customerApi.getCustomerByUserId(userId);
        return accountMapper.toResponse(accountLookup.ownedBy(accountId, customer.id()));
    }

    // HU13: se responde en cualquier estado (CA04 y CA05 piden reflejarlo, no rechazarlo)
    @Transactional(readOnly = true)
    public AccountResponse getMyAccountByNumber(UUID userId, String accountNumber) {
        CustomerView customer = customerApi.getCustomerByUserId(userId);
        return accountMapper.toResponse(accountLookup.byNumberOwnedBy(accountNumber, customer.id()));
    }

    // HU12: en cualquier estado, como el detalle, que ya incluye el saldo
    @Transactional(readOnly = true)
    public AccountBalanceResponse getMyBalanceByNumber(UUID userId, String accountNumber) {
        CustomerView customer = customerApi.getCustomerByUserId(userId);
        return accountMapper.toBalanceResponse(accountLookup.byNumberOwnedBy(accountNumber, customer.id()));
    }

    /**
     * Consultas del administrador: sin verificación de propiedad ni filtro por estado.
     * No pasan por customerApi: un ADMIN no tiene fila en customers, así que getCustomerByUserId fallaría.
     * Solo se deben llamar desde un endpoint protegido con @PreAuthorize("hasRole('ADMIN')").
     */
    @Transactional(readOnly = true)
    public AccountResponse getAnyAccountByNumber(String accountNumber) {
        return accountMapper.toResponse(accountLookup.byNumber(accountNumber));
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse getAnyBalanceByNumber(String accountNumber) {
        return accountMapper.toBalanceResponse(accountLookup.byNumber(accountNumber));
    }

    // La cuenta nace activa y sin saldo: la apertura no exige depósito inicial
    private AccountResponse open(UUID customerId) {
        List<Account> existing = accountRepository.findByCustomerIdOrderByCreatedAt(customerId);
        if (existing.size() >= MAX_ACCOUNTS_PER_CUSTOMER) {
            throw new AccountLimitReachedException("El cliente ya alcanzó el número máximo permitido de "
                    + MAX_ACCOUNTS_PER_CUSTOMER + " cuentas");
        }

        Account account = new Account();
        account.setCustomerId(customerId);
        account.setAccountNumber(accountNumberGenerator.next());
        account.setAccountIndex(nextIndex(existing));
        account.setStatus(accountCatalog.status(AccountStatusEnum.ACTIVE));
        account.setBalance(Account.INITIAL_BALANCE);

        return accountMapper.toResponse(accountRepository.save(account));
    }

    // Se toma del mayor ocupado y no del conteo, para no reutilizar una posición si alguna vez
    // se borrara una fila: el UNIQUE (customer_id, account_index) rechazaría el duplicado
    private static short nextIndex(List<Account> existing) {
        short highest = existing.stream()
                .map(Account::getAccountIndex)
                .max(Short::compareTo)
                .orElse((short) 0);
        return (short) (highest + 1);
    }
}
