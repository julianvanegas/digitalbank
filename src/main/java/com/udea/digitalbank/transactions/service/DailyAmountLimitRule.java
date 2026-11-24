package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.shared.exception.transactions.TransactionLimitExceededException;
import com.udea.digitalbank.transactions.domain.TransactionLimit;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Monto máximo acumulado en el día por las operaciones completadas desde la cuenta de origen. */
@Component
@Order(3)
class DailyAmountLimitRule implements LimitRule {

    private final DailyUsage usage;

    DailyAmountLimitRule(DailyUsage usage) {
        this.usage = usage;
    }

    @Override
    public boolean appliesTo(TransactionLimit limit) {
        return limit.hasDailyAmountLimit();
    }

    @Override
    public void check(LimitContext context) {
        BigDecimal used = usage.amount(context);
        TransactionLimit limit = context.limit();
        if (limit.exceedsDailyAmount(used, context.amount())) {
            throw new TransactionLimitExceededException("El monto supera el acumulado diario de "
                    + LimitRule.money(limit.getMaxDailyAmount()) + " para esta cuenta. Cupo disponible hoy: "
                    + LimitRule.money(limit.remainingDailyAmount(used)));
        }
    }
}
