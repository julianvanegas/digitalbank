package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.shared.exception.transactions.TransactionLimitExceededException;
import com.udea.digitalbank.transactions.domain.TransactionLimit;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Cantidad máxima de operaciones completadas en el día desde la cuenta de origen. */
@Component
@Order(2)
class DailyCountLimitRule implements LimitRule {

    private final DailyUsage usage;

    DailyCountLimitRule(DailyUsage usage) {
        this.usage = usage;
    }

    @Override
    public boolean appliesTo(TransactionLimit limit) {
        return limit.hasDailyCountLimit();
    }

    @Override
    public void check(LimitContext context) {
        if (context.limit().reachedDailyCount(usage.count(context))) {
            throw new TransactionLimitExceededException("Alcanzaste el máximo de "
                    + context.limit().getMaxDailyCount() + " operaciones diarias para esta cuenta");
        }
    }
}
