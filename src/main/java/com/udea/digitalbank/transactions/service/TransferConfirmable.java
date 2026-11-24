package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.auth.api.CodeCheckResult;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Cómo se completa una transferencia entre cuentas del banco al confirmarla: revalida los topes, mueve el dinero con
 * las dos cuentas bloqueadas y avisa al titular de la cuenta de origen con el comprobante por correo. El primer paso
 * es {@link TransferInitiationService}.
 */
@Component
class TransferConfirmable implements ConfirmableOperation {

    private final TransactionRepository transactionRepository;
    private final TransactionCatalog catalog;
    private final TransactionResponses responses;
    private final TransferParties parties;
    private final AccountApi accountApi;
    private final ApplicationEventPublisher eventPublisher;

    TransferConfirmable(TransactionRepository transactionRepository, TransactionCatalog catalog,
                        TransactionResponses responses, TransferParties parties, AccountApi accountApi,
                        ApplicationEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.catalog = catalog;
        this.responses = responses;
        this.parties = parties;
        this.accountApi = accountApi;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public TransactionTypeEnum type() {
        return TransactionTypeEnum.TRANSFER;
    }

    /** El comprobante por correo va al titular de la cuenta de origen, sea quien sea el ordenante. */
    @Override
    public TransactionResponse complete(TransferOperation operation, Transaction transaction) {
        parties.enforceLimits(operation, transaction.getSenderAccountId(), transaction.getAmount());
        accountApi.applyTransfer(transaction.getSenderAccountId(), transaction.getReceiverAccountId(),
                transaction.getAmount());
        transaction.complete(catalog.status(TransactionStatusEnum.COMPLETED));
        transactionRepository.save(transaction);

        TransactionResponse response = responses.of(transaction);
        eventPublisher.publishEvent(new TransferCompletedEvent(operation.codeOwnerId(),
                transaction.getReference(), response.getSenderAccountNumber(), response.getReceiverAccountNumber(),
                transaction.getAmount()));
        return response;
    }

    @Override
    public String wrongCode(CodeCheckResult check) {
        return TransferMessages.wrongCode(check.remainingAttempts());
    }

    @Override
    public String exhaustedAttempts() {
        return TransferMessages.EXHAUSTED_ATTEMPTS;
    }

    @Override
    public String expiredCode() {
        return TransferMessages.EXPIRED_CODE;
    }
}
