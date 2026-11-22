package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.auth.api.CodeCheckResult;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.springframework.stereotype.Component;

/**
 * Cómo se completa un retiro al confirmarlo: revalida los topes (si le aplican al ordenante) y debita la cuenta
 * con su fila bloqueada. Es un movimiento de un solo lado: no hay cuenta de destino ni correo de comprobante. El primer
 * paso es {@link WithdrawalInitiationService}.
 */
@Component
class WithdrawalConfirmable implements ConfirmableOperation {

    private final TransactionRepository transactionRepository;
    private final TransactionCatalog catalog;
    private final TransactionResponses responses;
    private final TransactionLimitService limitService;
    private final AccountApi accountApi;

    WithdrawalConfirmable(TransactionRepository transactionRepository, TransactionCatalog catalog,
                          TransactionResponses responses, TransactionLimitService limitService,
                          AccountApi accountApi) {
        this.transactionRepository = transactionRepository;
        this.catalog = catalog;
        this.responses = responses;
        this.limitService = limitService;
        this.accountApi = accountApi;
    }

    @Override
    public TransactionTypeEnum type() {
        return TransactionTypeEnum.WITHDRAWAL;
    }

    @Override
    public TransactionResponse complete(TransferOperation operation, Transaction transaction) {
        if (operation.enforceLimits()) {
            limitService.requireWithinLimits(accountApi.getAccount(transaction.getSenderAccountId()),
                    TransactionTypeEnum.WITHDRAWAL, transaction.getAmount());
        }
        AccountView debited = accountApi.applyWithdrawal(transaction.getSenderAccountId(), transaction.getAmount());
        transaction.complete(catalog.status(TransactionStatusEnum.COMPLETED));
        transactionRepository.save(transaction);
        return responses.of(transaction, debited.accountNumber(), null);
    }

    @Override
    public String wrongCode(CodeCheckResult check) {
        return WithdrawalMessages.wrongCode(check.remainingAttempts());
    }

    @Override
    public String exhaustedAttempts() {
        return WithdrawalMessages.EXHAUSTED_ATTEMPTS;
    }

    @Override
    public String expiredCode() {
        return WithdrawalMessages.EXPIRED_CODE;
    }
}
