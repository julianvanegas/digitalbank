package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.dto.AccountBalanceResponse;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.mapper.AccountMapper;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.shared.exception.accounts.AccountKeyCollisionException;
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
    private final AccountKeyGenerator accountKeyGenerator;
    private final AccountCatalog accountCatalog;

    public AccountService(AccountRepository accountRepository,
                          CustomerApi customerApi,
                          AccountMapper accountMapper,
                          AccountLookup accountLookup,
                          AccountNumberGenerator accountNumberGenerator,
                          AccountKeyGenerator accountKeyGenerator,
                          AccountCatalog accountCatalog) {
        this.accountRepository = accountRepository;
        this.customerApi = customerApi;
        this.accountMapper = accountMapper;
        this.accountLookup = accountLookup;
        this.accountNumberGenerator = accountNumberGenerator;
        this.accountKeyGenerator = accountKeyGenerator;
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
        return open(customer);
    }

    /**
     * Apertura hecha por un administrador en nombre de un cliente.
     * Solo se debe llamar desde un endpoint protegido con @PreAuthorize("hasRole('ADMIN')").
     */
    @Transactional
    public AccountResponse openForCustomer(UUID customerId) {
        // La fachada de customer valida en un solo paso que el cliente exista y esté ACTIVE
        CustomerView customer = customerApi.requireActiveCustomer(customerId);
        return open(customer);
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

    /**
     * Todas las cuentas de un cliente a partir de su documento, en orden de apertura y en cualquier estado.
     * Consulta del administrador: el camino es documento → cliente → cuentas, sin pasar por getCustomerByUserId
     * (un ADMIN no tiene fila en customers). Un cliente sin cuentas devuelve una lista vacía, no un error;
     * un documento sin cliente propaga CustomerNotFoundException (404).
     * <p>
     * La búsqueda es por coincidencia exacta del número: el registro guarda el documento tal como lo escribe
     * el cliente, sin normalizar, así que "1.234.567" y "1234567" no son el mismo. Un documentNumber vacío
     * responde 404 y no 400, a propósito: no hay cliente con ese documento.
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByDocument(Short documentTypeId, String documentNumber) {
        CustomerView customer = customerApi.getCustomerByDocument(documentTypeId, documentNumber);
        return accountRepository.findByCustomerIdOrderByCreatedAt(customer.id()).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    /**
     * Saldo de cada cuenta de un cliente a partir de su documento, con su estado al lado: no se suman,
     * porque mezclaría dinero disponible con dinero que no se puede mover. Mismo camino y mismas reglas que
     * getAccountsByDocument.
     * <p>
     * La búsqueda es por coincidencia exacta del número: el registro guarda el documento tal como lo escribe
     * el cliente, sin normalizar, así que "1.234.567" y "1234567" no son el mismo. Un documentNumber vacío
     * responde 404 y no 400, a propósito: no hay cliente con ese documento.
     */
    @Transactional(readOnly = true)
    public List<AccountBalanceResponse> getBalancesByDocument(Short documentTypeId, String documentNumber) {
        CustomerView customer = customerApi.getCustomerByDocument(documentTypeId, documentNumber);
        return accountRepository.findByCustomerIdOrderByCreatedAt(customer.id()).stream()
                .map(accountMapper::toBalanceResponse)
                .toList();
    }

    // La cuenta nace activa y sin saldo: la apertura no exige depósito inicial
    private AccountResponse open(CustomerView customer) {
        List<Account> existing = accountRepository.findByCustomerIdOrderByCreatedAt(customer.id());
        if (existing.size() >= MAX_ACCOUNTS_PER_CUSTOMER) {
            throw new AccountLimitReachedException("El cliente ya alcanzó el número máximo permitido de "
                    + MAX_ACCOUNTS_PER_CUSTOMER + " cuentas");
        }

        short index = nextIndex(existing);

        Account account = new Account();
        account.setCustomerId(customer.id());
        account.setAccountNumber(accountNumberGenerator.next());
        // La llave se deduce del titular, así que necesita su nombre y documento, no solo el id
        account.setAccountKey(requireFreeKey(accountKeyGenerator.build(
                customer.firstNames(), customer.documentNumber(), index)));
        account.setAccountIndex(index);
        account.setStatus(accountCatalog.status(AccountStatusEnum.ACTIVE));
        account.setBalance(Account.INITIAL_BALANCE);

        return accountMapper.toResponse(accountRepository.save(account));
    }

    /**
     * La llave de CA01 se deduce del titular, así que si choca con la de otro cliente no sirve
     * reintentar: siempre daría el mismo valor. Se comprueba antes de guardar para distinguir este caso
     * del choque genérico del UNIQUE y poder decir qué pasó; el UNIQUE sigue siendo la garantía.
     */
    private String requireFreeKey(String accountKey) {
        if (accountRepository.existsByAccountKey(accountKey)) {
            throw new AccountKeyCollisionException("La llave " + accountKey + " ya pertenece a otra cuenta: "
                    + "otro cliente comparte el inicio del primer nombre y los últimos dígitos del documento. "
                    + "Requiere asignación manual.");
        }
        return accountKey;
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
