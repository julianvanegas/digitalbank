package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.auth.api.VerificationApi;
import com.udea.digitalbank.shared.exception.accounts.AccountNotActiveException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.exception.accounts.InsufficientFundsException;
import com.udea.digitalbank.shared.exception.transactions.TransactionLimitExceededException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Primer paso del retiro (HU19): valida la operación y la deja {@code PENDING} mientras el segundo factor la autoriza.
 * Sigue el flujo de {@link TransferInitiationService}, para un movimiento de un solo lado: el dinero sale de la cuenta
 * y no llega a ninguna otra. El segundo paso es {@link TransactionConfirmationService}.
 *
 * <p><b>Qué se registra.</b> Las validaciones de este paso fallan sin dejar fila: un retiro imposible no llega a ser
 * una transacción.
 *
 * <p><b>Cliente y administrador.</b> Un cliente retira de sus propias cuentas y le aplican los topes
 * ({@link TransactionLimitService}). Un administrador puede retirar de la cuenta de cualquier cliente y no se le
 * aplican los topes. En ambos casos el segundo factor es del <b>titular de la cuenta</b>. Lo que cambia según el rol se
 * resuelve con {@link TransferParties#operationFor}, el mismo lugar que lo decide en las transferencias.
 */
@Service
public class WithdrawalInitiationService {

    private final TransactionRepository transactionRepository;
    private final TransactionCatalog catalog;
    private final TransactionResponses responses;
    private final TransactionReferenceGenerator referenceGenerator;
    private final TransferParties parties;
    private final PendingTransfers pendingTransfers;
    private final TransactionLimitService limitService;
    private final AccountApi accountApi;
    private final VerificationApi verificationApi;
    private final Clock clock;

    public WithdrawalInitiationService(TransactionRepository transactionRepository,
                                       TransactionCatalog catalog,
                                       TransactionResponses responses,
                                       TransactionReferenceGenerator referenceGenerator,
                                       TransferParties parties,
                                       PendingTransfers pendingTransfers,
                                       TransactionLimitService limitService,
                                       AccountApi accountApi,
                                       VerificationApi verificationApi,
                                       Clock clock) {
        this.transactionRepository = transactionRepository;
        this.catalog = catalog;
        this.responses = responses;
        this.referenceGenerator = referenceGenerator;
        this.parties = parties;
        this.pendingTransfers = pendingTransfers;
        this.limitService = limitService;
        this.accountApi = accountApi;
        this.verificationApi = verificationApi;
        this.clock = clock;
    }

    /**
     * Valida el retiro, lo deja {@code PENDING} y envía al correo del titular de la cuenta el código de segundo factor
     * que lo autoriza.
     *
     * <p>Las validaciones se hacen en este orden: cuenta (del ordenante si es cliente, de cualquier cliente si es
     * administrador) y activa; saldo suficiente (CA02); topes (CA03, solo si el ordenante es cliente). La comprobación
     * del saldo y de los topes permite fallar rápido, pero la definitiva ocurre al confirmar.
     *
     * <p>Si el titular ya tenía otra transacción {@code PENDING} (un retiro o una transferencia), esa se rechaza: solo
     * hay un código vigente por titular y el nuevo reemplaza al anterior.
     *
     * @param actor         usuario autenticado que ordena el retiro: un cliente o un administrador
     * @param accountNumber número de la cuenta de la que sale el dinero
     * @param amount        monto positivo del retiro
     * @return el comprobante en estado {@code PENDING}, con el instante en que vence el código
     * @throws AccountNotFoundException          si la cuenta no existe o, para un cliente, no es suya
     * @throws AccountNotActiveException         si la cuenta no está activa
     * @throws InsufficientFundsException        si el saldo no alcanza (CA02)
     * @throws TransactionLimitExceededException si un cliente supera alguno de los topes de su cuenta (CA03)
     */
    @Transactional
    public TransactionResponse initiate(Actor actor, String accountNumber, BigDecimal amount) {
        BigDecimal scaled = amount.setScale(2);
        AccountView account = activeAccount(actor, accountNumber);
        requireFunds(account, scaled);
        TransferOperation operation = parties.operationFor(actor, account.id());
        if (operation.enforceLimits()) {
            limitService.requireWithinLimits(account, TransactionTypeEnum.WITHDRAWAL, scaled);
        }
        pendingTransfers.rejectPendingOf(account.customerId());

        Transaction transaction = savePending(actor, account, scaled);

        // Se envía al final: emitir el código invalida el anterior, y el correo describe la operación que se autoriza
        verificationApi.sendTransactionCode(operation.codeOwnerId(), WithdrawalMessages.codeDetail(
                operation.codeDetailPrefix(), scaled, accountNumber, transaction.getReference()));

        TransactionResponse response = responses.of(transaction, accountNumber, null);
        response.setExpiresAt(transaction.getCreatedAt().plusMinutes(verificationApi.transactionCodeTtlMinutes()));
        return response;
    }

    /**
     * La cuenta que el ordenante puede usar, activa. Un cliente solo puede usar las suyas (una ajena responde como
     * inexistente, para no confirmar que el número existe); un administrador, la de cualquier cliente.
     */
    private AccountView activeAccount(Actor actor, String accountNumber) {
        AccountView account = actor.admin()
                ? accountApi.getAccountByNumber(accountNumber)
                : accountApi.requireOwnedAccount(actor.userId(), accountNumber);
        if (!account.isActive()) {
            throw new AccountNotActiveException(WithdrawalMessages.accountNotActive(account.status()));
        }
        return account;
    }

    private static void requireFunds(AccountView account, BigDecimal amount) {
        if (account.balance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(WithdrawalMessages.INSUFFICIENT_FUNDS);
        }
    }

    /** Guarda el retiro {@code PENDING}. El id se asigna antes porque la referencia se deriva de él. */
    private Transaction savePending(Actor actor, AccountView account, BigDecimal amount) {
        UUID transactionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(clock);
        return transactionRepository.save(Transaction.withdrawal(
                transactionId, actor.userId(), account.id(), amount,
                referenceGenerator.forWithdrawal(transactionId, now),
                catalog.type(TransactionTypeEnum.WITHDRAWAL), catalog.status(TransactionStatusEnum.PENDING), now));
    }
}
