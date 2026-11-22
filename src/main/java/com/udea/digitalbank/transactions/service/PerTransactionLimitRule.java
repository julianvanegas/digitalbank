package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.shared.exception.transactions.TransactionLimitExceededException;
import com.udea.digitalbank.transactions.domain.TransactionLimit;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Máximo de una sola operación. No consulta la base: se evalúa primero. */
@Component
@Order(1)
class PerTransactionLimitRule implements LimitRule {

    @Override
    public boolean appliesTo(TransactionLimit limit) {
        return limit.hasPerTransactionLimit();
    }

    @Override
    public void check(LimitContext context) {
        if (context.limit().exceedsPerTransaction(context.amount())) {
            throw new TransactionLimitExceededException("El monto supera el máximo por operación de "
                    + LimitRule.money(context.limit().getMaxPerTransaction()));
        }
    }
}
