package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.auth.api.VerificationApi;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

/**
 * Arma el {@link TransactionConfirmationService} de las pruebas con las dos operaciones reales (transferencia y retiro)
 * sobre los colaboradores que cada prueba ya tiene como mocks, igual que lo hace Spring con todos los
 * {@link ConfirmableOperation}.
 */
final class ConfirmationServices {

    private ConfirmationServices() {
    }

    static TransactionConfirmationService of(TransactionRepository transactionRepository, TransactionCatalog catalog,
                                             TransactionResponses responses, TransferParties parties,
                                             PendingTransfers pendingTransfers, TransactionLimitService limitService,
                                             AccountApi accountApi, VerificationApi verificationApi,
                                             ApplicationEventPublisher eventPublisher) {
        return new TransactionConfirmationService(transactionRepository, responses, parties, pendingTransfers,
                verificationApi, List.of(
                        new TransferConfirmable(transactionRepository, catalog, responses, parties, accountApi,
                                eventPublisher),
                        new WithdrawalConfirmable(transactionRepository, catalog, responses, limitService,
                                accountApi)));
    }
}
