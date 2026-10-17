package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.domain.AccountAction;
import com.udea.digitalbank.accounts.domain.AccountActionType;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.mapper.AccountMapper;
import com.udea.digitalbank.accounts.repository.AccountActionRepository;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.shared.exception.InvalidRequestException;
import com.udea.digitalbank.shared.security.Actor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cambios de estado de una cuenta: bloquear, desbloquear y cerrar. Cada uno lee la fila con bloqueo, valida el
 * estado de origen, registra la acción en el historial y avisa al titular tras confirmarse la transacción.
 */
@Service
public class AccountLifecycleService {

    // Motivo que queda en el historial de un cierre: dice quién lo hizo
    static final String CLOSED_BY_CUSTOMER = "Cierre solicitado por el cliente";
    static final String CLOSED_BY_ADMIN = "Cierre realizado por un administrador";

    private final AccountRepository accountRepository;
    private final AccountActionRepository accountActionRepository;
    private final CustomerApi customerApi;
    private final AccountMapper accountMapper;
    private final AccountLookup accountLookup;
    private final AccountCatalog accountCatalog;
    private final ApplicationEventPublisher eventPublisher;

    public AccountLifecycleService(AccountRepository accountRepository,
                                   AccountActionRepository accountActionRepository,
                                   CustomerApi customerApi,
                                   AccountMapper accountMapper,
                                   AccountLookup accountLookup,
                                   AccountCatalog accountCatalog,
                                   ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.accountActionRepository = accountActionRepository;
        this.customerApi = customerApi;
        this.accountMapper = accountMapper;
        this.accountLookup = accountLookup;
        this.accountCatalog = accountCatalog;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Bloqueo temporal de una cuenta por parte de un administrador (HU15). Deja la cuenta en BLOCKED
     * y registra la acción BLOCKED con su motivo y el administrador; el registro no se modifica después.
     * Solo se debe llamar desde un endpoint que la seguridad reserve a administradores.
     * La fila de la cuenta se bloquea al leerla: de dos bloqueos simultáneos, el segundo espera y ve la cuenta
     * ya BLOCKED, así que se rechaza y no queda una acción duplicada en el registro.
     * <p>
     * Solo se bloquea una cuenta ACTIVE: una ya bloqueada se rechaza por CA02, y una inactiva o
     * cerrada también, porque CA01 parte de que la cuenta está activa.
     */
    @Transactional
    public AccountResponse blockAccount(String accountNumber, String reason, UUID actingUserId) {
        // El motivo también lo exige el DTO y el CHECK de la tabla; aquí se protege la entrada al caso de uso
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("El motivo del bloqueo es obligatorio");
        }

        Account account = accountLookup.byNumberForUpdate(accountNumber);
        account.block(accountCatalog.status(AccountStatusEnum.BLOCKED));

        AccountAction block = accountActionRepository.save(
                AccountAction.blocked(account.getId(), reason.trim(), actingUserId));

        Account blocked = accountRepository.save(account);
        // El aviso al titular sale tras confirmarse la transacción, para no bloquear la respuesta
        // ni deshacer el bloqueo si el correo falla
        eventPublisher.publishEvent(new AccountActionEvent(
                blocked.getCustomerId(), blocked.getAccountNumber(), AccountActionType.BLOCKED, block.getReason()));

        return accountMapper.toResponse(blocked);
    }

    /**
     * Desbloqueo de una cuenta por parte de un administrador: la devuelve de BLOCKED a ACTIVE.
     * Solo se debe llamar desde un endpoint que la seguridad reserve a administradores.
     * Igual que el bloqueo, lee la cuenta con la fila bloqueada para que dos desbloqueos simultáneos no se repitan.
     * <p>
     * Solo se desbloquea una cuenta BLOCKED: una activa, inactiva o cerrada se rechaza, para que esta
     * operación no reactive por accidente una cuenta que no fue bloqueada. Queda una acción UNLOCKED
     * en el registro, con el motivo, el administrador que lo ordenó (del token) y la fecha, y se avisa al titular.
     */
    @Transactional
    public AccountResponse unblockAccount(String accountNumber, String reason, UUID actingUserId) {
        // El motivo también lo exige el DTO y el CHECK de la tabla; aquí se protege la entrada al caso de uso
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("El motivo del desbloqueo es obligatorio");
        }

        Account account = accountLookup.byNumberForUpdate(accountNumber);
        account.unlock(accountCatalog.status(AccountStatusEnum.ACTIVE));

        String trimmedReason = reason.trim();
        accountActionRepository.save(AccountAction.unlocked(account.getId(), trimmedReason, actingUserId));

        Account unlocked = accountRepository.save(account);
        // Igual que el bloqueo: el aviso al titular sale tras confirmarse la transacción
        eventPublisher.publishEvent(new AccountActionEvent(
                unlocked.getCustomerId(), unlocked.getAccountNumber(), AccountActionType.UNLOCKED,
                trimmedReason));

        return accountMapper.toResponse(unlocked);
    }

    /**
     * Cierre de una cuenta. Un cliente cierra las suyas (una ajena responde igual que una inexistente); un
     * administrador, cualquiera, sin pasar por customerApi. Queda registrado quién la cerró: el motivo dice si fue
     * el cliente o un administrador. Las reglas están en {@link #close(Account, String, UUID)}.
     */
    @Transactional
    public AccountResponse closeAccount(Actor actor, String accountNumber) {
        if (actor.admin()) {
            return close(accountLookup.byNumberForUpdate(accountNumber), CLOSED_BY_ADMIN, actor.userId());
        }
        CustomerView customer = customerApi.getCustomerByUserId(actor.userId());
        return close(accountLookup.byNumberOwnedByForUpdate(accountNumber, customer.id()),
                CLOSED_BY_CUSTOMER, actor.userId());
    }

    /**
     * Deja la cuenta en CLOSED con su fecha de cierre (la base exige las dos cosas a la vez), registra la acción
     * CLOSED con quién la hizo y avisa al titular. La fila llega bloqueada, así que dos cierres simultáneos se
     * turnan y el segundo ya la ve cerrada.
     * <p>
     * Solo se cierra una cuenta ACTIVE o INACTIVE y con saldo cero: con dinero hay que retirarlo antes, y una
     * bloqueada hay que desbloquearla primero, para que cerrarla no sirva para esquivar un bloqueo.
     */
    private AccountResponse close(Account account, String reason, UUID actingUserId) {
        account.close(accountCatalog.status(AccountStatusEnum.CLOSED), LocalDateTime.now());
        accountActionRepository.save(AccountAction.closed(account.getId(), reason, actingUserId));

        Account closed = accountRepository.save(account);
        // Igual que el bloqueo: el aviso al titular sale tras confirmarse la transacción
        eventPublisher.publishEvent(new AccountActionEvent(
                closed.getCustomerId(), closed.getAccountNumber(), AccountActionType.CLOSED, reason));
        return accountMapper.toResponse(closed);
    }
}
