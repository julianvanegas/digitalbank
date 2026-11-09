package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.dto.AccountBalanceResponse;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.mapper.AccountMapper;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.shared.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Consultas de cuentas, todas de solo lectura: las propias del cliente y las que el administrador mira de cualquiera.
 */
@Service
public class AccountQueryService {

    // Estados que el cliente ve en su propio listado (HU11); el resto solo se consulta por número
    private static final Set<AccountStatusEnum> VISIBLE_TO_CUSTOMER =
            EnumSet.of(AccountStatusEnum.ACTIVE, AccountStatusEnum.BLOCKED);

    private final AccountRepository accountRepository;
    private final CustomerApi customerApi;
    private final AccountMapper accountMapper;
    private final AccountLookup accountLookup;

    public AccountQueryService(AccountRepository accountRepository,
                               CustomerApi customerApi,
                               AccountMapper accountMapper,
                               AccountLookup accountLookup) {
        this.accountRepository = accountRepository;
        this.customerApi = customerApi;
        this.accountMapper = accountMapper;
        this.accountLookup = accountLookup;
    }

    /**
     * Las cuentas del cliente autenticado que están ACTIVE o BLOCKED, en orden de apertura.
     * <p>
     * HU11 CA01 dice textualmente "todas sus cuentas", pero el alcance confirmado por el dueño del producto es mostrar
     * al cliente solo las ACTIVE y las BLOCKED: las INACTIVE y las CLOSED no aparecen en el listado. Siguen siendo
     * consultables por su número (HU12 saldo, HU13 detalle). El administrador sigue viendo todas por documento.
     * <p>
     * Se filtra aquí y no en la consulta para conservar la misma consulta (y su orden por created_at). La lista de
     * estados es por la positiva: un estado nuevo queda oculto al cliente hasta que se decida mostrarlo.
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts(UUID userId) {
        CustomerView customer = customerApi.getCustomerByUserId(userId);
        return accountRepository.findByCustomerIdOrderByCreatedAt(customer.id()).stream()
                .filter(account -> VISIBLE_TO_CUSTOMER.stream().anyMatch(account::is))
                .map(accountMapper::toResponse)
                .toList();
    }

    /**
     * Detalle de una cuenta por su número, en cualquier estado (se refleja el estado, no se rechaza).
     * Un cliente solo la obtiene si es suya (una ajena responde igual que una inexistente); un administrador, de
     * cualquiera, sin pasar por customerApi: un ADMIN no tiene fila en customers.
     */
    @Transactional(readOnly = true)
    public AccountResponse getAccount(Actor actor, String accountNumber) {
        return accountMapper.toResponse(readableAccount(actor, accountNumber));
    }

    /** En cualquier estado, con las mismas reglas de acceso que el detalle, que ya incluye el saldo. */
    @Transactional(readOnly = true)
    public AccountBalanceResponse getBalance(Actor actor, String accountNumber) {
        return accountMapper.toBalanceResponse(readableAccount(actor, accountNumber));
    }

    private Account readableAccount(Actor actor, String accountNumber) {
        if (actor.admin()) {
            return accountLookup.byNumber(accountNumber);
        }
        CustomerView customer = customerApi.getCustomerByUserId(actor.userId());
        return accountLookup.byNumberOwnedBy(accountNumber, customer.id());
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
}
