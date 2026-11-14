package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.transactions.domain.Transaction;
import org.springframework.stereotype.Component;

/**
 * Quién puede ver una transacción. La regla vive aquí y no en cada servicio que la necesita: una transacción la ve su
 * ordenante, el titular de la cuenta de origen (por ejemplo, una transferencia que un administrador ordenó a su
 * nombre) y cualquier administrador. Quién puede confirmarla es más estricto y lo decide la propia transacción
 * ({@link Transaction#isOrderedBy}): solo quien la ordenó.
 */
@Component
class TransactionAccess {

    private final AccountApi accountApi;

    TransactionAccess(AccountApi accountApi) {
        this.accountApi = accountApi;
    }

    /**
     * @param actor       usuario autenticado
     * @param transaction transacción que quiere ver
     * @return {@code true} si es administrador, quien la ordenó o el titular de su cuenta de origen
     */
    boolean canView(Actor actor, Transaction transaction) {
        return actor.admin() || transaction.isOrderedBy(actor.userId()) || isSenderOwner(actor, transaction);
    }

    // Una consignación no tiene cuenta de origen
    private boolean isSenderOwner(Actor actor, Transaction transaction) {
        return transaction.getSenderAccountId() != null
                && accountApi.getOwnerUserId(transaction.getSenderAccountId()).equals(actor.userId());
    }
}
