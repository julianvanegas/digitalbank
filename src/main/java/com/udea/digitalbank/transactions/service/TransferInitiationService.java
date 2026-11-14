package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.auth.api.VerificationApi;
import com.udea.digitalbank.shared.exception.accounts.AccountNotActiveException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.exception.accounts.InsufficientFundsException;
import com.udea.digitalbank.shared.exception.transactions.InvalidReceiverAccountException;
import com.udea.digitalbank.shared.exception.transactions.SameAccountTransferException;
import com.udea.digitalbank.shared.exception.transactions.TransactionLimitExceededException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.dto.TransferRequest;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Primer paso de la transferencia entre cuentas del banco: valida la operación y la deja {@code PENDING} mientras el
 * segundo factor la autoriza. El segundo paso es {@link TransferConfirmationService}.
 *
 * <p><b>Qué se registra.</b> Las validaciones de este paso fallan sin dejar fila: una transferencia imposible no llega
 * a ser una transacción.
 *
 * <p><b>Cliente y administrador.</b> Un cliente transfiere desde sus propias cuentas y le aplican los topes
 * ({@link TransactionLimitService}). Un administrador puede transferir desde la cuenta de cualquier cliente y no se le
 * aplican los topes. En ambos casos el segundo factor es del <b>titular de la cuenta de origen</b>. Lo que cambia según
 * el rol se decide en {@link TransferParties}.
 */
@Service
public class TransferInitiationService {

    private final TransactionRepository transactionRepository;
    private final TransactionCatalog catalog;
    private final TransactionResponses responses;
    private final TransactionReferenceGenerator referenceGenerator;
    private final TransferParties parties;
    private final PendingTransfers pendingTransfers;
    private final VerificationApi verificationApi;
    private final Clock clock;

    public TransferInitiationService(TransactionRepository transactionRepository,
                                     TransactionCatalog catalog,
                                     TransactionResponses responses,
                                     TransactionReferenceGenerator referenceGenerator,
                                     TransferParties parties,
                                     PendingTransfers pendingTransfers,
                                     VerificationApi verificationApi,
                                     Clock clock) {
        this.transactionRepository = transactionRepository;
        this.catalog = catalog;
        this.responses = responses;
        this.referenceGenerator = referenceGenerator;
        this.parties = parties;
        this.pendingTransfers = pendingTransfers;
        this.verificationApi = verificationApi;
        this.clock = clock;
    }

    /**
     * Valida la transferencia, la deja {@code PENDING} y envía al correo del titular de la cuenta de origen el código
     * de segundo factor que la autoriza.
     *
     * <p>Las validaciones se hacen en este orden: cuentas de origen y destino distintas; cuenta de origen (del
     * ordenante si es cliente, de cualquier cliente si es administrador) y activa; cuenta de destino existente y
     * activa; saldo suficiente; topes (solo si el ordenante es cliente). La comprobación del saldo y de los topes
     * permite fallar rápido, pero la definitiva ocurre al confirmar.
     *
     * <p>Si el titular de la cuenta de origen ya tenía otra transferencia {@code PENDING}, esa se rechaza: solo hay un
     * código vigente por titular y el nuevo reemplaza al anterior.
     *
     * @param actor   usuario autenticado que ordena la transferencia: un cliente o un administrador
     * @param request cuentas de origen y destino, y monto
     * @return el comprobante en estado {@code PENDING}, con el instante en que vence el código
     * @throws SameAccountTransferException      si origen y destino son la misma cuenta
     * @throws AccountNotFoundException          si la cuenta de origen no existe o no es del ordenante
     * @throws AccountNotActiveException         si la cuenta de origen no está activa
     * @throws InvalidReceiverAccountException   si la cuenta de destino no existe o no está activa
     * @throws InsufficientFundsException        si el saldo de la cuenta de origen no alcanza
     * @throws TransactionLimitExceededException si un cliente supera alguno de los topes de su cuenta
     */
    @Transactional
    public TransactionResponse initiate(Actor actor, TransferRequest request) {
        String senderNumber = request.getSenderAccountNumber();
        String receiverNumber = request.getReceiverAccountNumber();
        BigDecimal amount = request.getAmount().setScale(2);
        parties.requireDifferentAccounts(senderNumber, receiverNumber);

        AccountView sender = parties.activeSender(actor, senderNumber);
        AccountView receiver = parties.activeReceiver(receiverNumber);
        parties.requireFunds(sender, amount);
        TransferOperation operation = parties.operationFor(actor, sender.id());
        parties.enforceLimits(operation, sender, amount);
        pendingTransfers.rejectPendingOf(sender.customerId());

        Transaction transaction = savePending(actor, sender, receiver, amount);

        // Se envía al final: emitir el código invalida el anterior, y el correo describe la operación que se autoriza
        verificationApi.sendTransactionCode(operation.codeOwnerId(), TransferMessages.codeDetail(
                operation.codeDetailPrefix(), amount, receiverNumber, transaction.getReference()));

        TransactionResponse response = responses.of(transaction, senderNumber, receiverNumber);
        response.setExpiresAt(transaction.getCreatedAt().plusMinutes(verificationApi.transactionCodeTtlMinutes()));
        return response;
    }

    /** Guarda la transferencia {@code PENDING}. El id se asigna antes porque la referencia se deriva de él. */
    private Transaction savePending(Actor actor, AccountView sender, AccountView receiver, BigDecimal amount) {
        UUID transactionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(clock);
        return transactionRepository.save(Transaction.transfer(
                transactionId, actor.userId(), sender.id(), receiver.id(), amount,
                referenceGenerator.forTransfer(transactionId, now),
                catalog.type(TransactionTypeEnum.TRANSFER), catalog.status(TransactionStatusEnum.PENDING), now));
    }
}
