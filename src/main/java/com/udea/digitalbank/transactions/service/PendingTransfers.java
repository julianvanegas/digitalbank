package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.auth.api.VerificationApi;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Las transferencias que están a la espera de su código y cómo se cancelan: una por una al confirmar o al ser
 * reemplazadas por otra, y en bloque cuando su código vence. Lo comparten el inicio y la confirmación de la
 * transferencia y el barrido programado.
 */
@Component
class PendingTransfers {

    private final TransactionRepository transactionRepository;
    private final TransactionCatalog catalog;
    private final AccountApi accountApi;
    private final VerificationApi verificationApi;
    private final Clock clock;

    PendingTransfers(TransactionRepository transactionRepository, TransactionCatalog catalog, AccountApi accountApi,
                     VerificationApi verificationApi, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.catalog = catalog;
        this.accountApi = accountApi;
        this.verificationApi = verificationApi;
        this.clock = clock;
    }

    /** Rechaza la transacción y guarda el cambio. */
    void reject(Transaction transaction) {
        transaction.reject(catalog.status(TransactionStatusEnum.REJECTED));
        transactionRepository.save(transaction);
    }

    /**
     * Rechaza las transferencias pendientes que salen de las cuentas del cliente, porque el código nuevo reemplaza al
     * anterior; ver {@code findPendingFromAccountsForUpdate}.
     */
    void rejectPendingOf(UUID customerId) {
        List<UUID> accountIds = accountApi.getAccountsOfCustomer(customerId).stream().map(AccountView::id).toList();
        transactionRepository.findPendingFromAccountsForUpdate(accountIds, catalog.status(TransactionStatusEnum.PENDING))
                .forEach(this::reject);
    }

    /**
     * Rechaza las transferencias que siguen {@code PENDING} cuando el código de verificación ya ha vencido.
     *
     * <p>Es una limpieza periódica y no una regla de seguridad: una confirmación tardía igualmente se rechaza por
     * código vencido. Evita que queden pendientes indefinidamente las transferencias que nadie confirma.
     *
     * @return cantidad de transferencias rechazadas
     */
    @Transactional
    int rejectExpired() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(verificationApi.transactionCodeTtlMinutes());
        return transactionRepository.rejectPendingCreatedBefore(cutoff,
                catalog.status(TransactionStatusEnum.PENDING), catalog.status(TransactionStatusEnum.REJECTED));
    }
}
