package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountTypeEnum;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.mapper.AccountMapper;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.shared.exception.InvalidRequestException;
import com.udea.digitalbank.shared.exception.accounts.AccountLimitReachedException;
import com.udea.digitalbank.shared.exception.accounts.AccountOwnershipException;
import com.udea.digitalbank.shared.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Apertura de cuentas: quién puede abrirlas, el cupo de tres abiertas y la asignación de número, llave y posición. */
@Service
public class AccountOpeningService {

    static final int MAX_ACCOUNTS_PER_CUSTOMER = 3;

    private final AccountRepository accountRepository;
    private final CustomerApi customerApi;
    private final AccountMapper accountMapper;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AccountKeyGenerator accountKeyGenerator;
    private final AccountCatalog accountCatalog;

    public AccountOpeningService(AccountRepository accountRepository,
                                 CustomerApi customerApi,
                                 AccountMapper accountMapper,
                                 AccountNumberGenerator accountNumberGenerator,
                                 AccountKeyGenerator accountKeyGenerator,
                                 AccountCatalog accountCatalog) {
        this.accountRepository = accountRepository;
        this.customerApi = customerApi;
        this.accountMapper = accountMapper;
        this.accountNumberGenerator = accountNumberGenerator;
        this.accountKeyGenerator = accountKeyGenerator;
        this.accountCatalog = accountCatalog;
    }

    /**
     * Apertura de una cuenta. Un cliente abre la suya: el titular se deduce del token, nunca del cuerpo, y si llega
     * un customerId ajeno se rechaza en vez de abrir la cuenta equivocada (no hace falta comprobar que esté activo:
     * el filtro del JWT ya rechaza cualquier token cuyo usuario no lo esté). Un administrador la abre en nombre de
     * un cliente y entonces el customerId es obligatorio y el cliente debe estar ACTIVE.
     */
    @Transactional
    public AccountResponse open(Actor actor, UUID requestedCustomerId, AccountTypeEnum accountType) {
        if (actor.admin()) {
            if (requestedCustomerId == null) {
                throw new InvalidRequestException("Un administrador debe indicar el customerId del titular de la cuenta");
            }
            // La fachada de customer valida en un solo paso que el cliente exista y esté ACTIVE
            return openFor(customerApi.requireActiveCustomer(requestedCustomerId), accountType);
        }
        CustomerView customer = customerApi.getCustomerByUserId(actor.userId());
        if (requestedCustomerId != null && !requestedCustomerId.equals(customer.id())) {
            throw new AccountOwnershipException("No puedes abrir una cuenta a nombre de otro cliente");
        }
        return openFor(customer, accountType);
    }

    // La cuenta nace activa y sin saldo: la apertura no exige depósito inicial. Sin tipo indicado es de ahorros
    private AccountResponse openFor(CustomerView customer, AccountTypeEnum requestedType) {
        // Las cuentas cerradas no cuentan para el límite: cerrar una libera su cupo
        List<Account> open = accountRepository.findByCustomerIdOrderByCreatedAt(customer.id()).stream()
                .filter(a -> !a.getStatus().is(AccountStatusEnum.CLOSED))
                .toList();
        if (open.size() >= MAX_ACCOUNTS_PER_CUSTOMER) {
            throw new AccountLimitReachedException("El cliente ya alcanzó el número máximo permitido de "
                    + MAX_ACCOUNTS_PER_CUSTOMER + " cuentas abiertas");
        }

        short index = firstFreeIndex(open);

        // La llave se deduce del titular, así que necesita su nombre y documento, no solo el id
        Account account = Account.open(
                customer.id(),
                accountNumberGenerator.next(),
                accountKeyGenerator.build(customer.firstNames(), customer.documentNumber()),
                index,
                accountCatalog.type(requestedType == null ? AccountTypeEnum.SAVINGS : requestedType),
                accountCatalog.status(AccountStatusEnum.ACTIVE));

        return accountMapper.toResponse(accountRepository.save(account));
    }

    // La primera posición libre entre las cuentas abiertas, para reutilizar la de una cuenta cerrada. El UNIQUE
    // parcial (customer_id, account_index) de la base rechaza el duplicado si dos aperturas eligen la misma
    private static short firstFreeIndex(List<Account> open) {
        for (short index = 1; index <= MAX_ACCOUNTS_PER_CUSTOMER; index++) {
            short candidate = index;
            if (open.stream().noneMatch(a -> a.getAccountIndex() == candidate)) {
                return candidate;
            }
        }
        // No se llega aquí: con menos de MAX cuentas abiertas siempre queda una posición libre
        throw new IllegalStateException("No hay una posición libre entre las cuentas abiertas del cliente");
    }
}
