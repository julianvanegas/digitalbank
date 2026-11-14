package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Cuánto ha movido una cuenta en un día: lo que consumen las reglas diarias de {@link LimitRule}. Concentra aquí el
 * acceso al repositorio y al catálogo, y la definición de consumo (solo transacciones {@code COMPLETED} del tipo
 * indicado que salieron de la cuenta), para que las reglas sean solo comparaciones.
 */
@Component
class DailyUsage {

    private final TransactionRepository transactionRepository;
    private final TransactionCatalog catalog;

    DailyUsage(TransactionRepository transactionRepository, TransactionCatalog catalog) {
        this.transactionRepository = transactionRepository;
        this.catalog = catalog;
    }

    /**
     * @param context la operación, con la cuenta de origen, el tipo de movimiento y el día que se mide
     * @return cantidad de operaciones completadas del tipo indicado desde la cuenta en el día
     */
    long count(LimitContext context) {
        return transactionRepository.countCompletedFromAccount(context.account().id(), catalog.type(context.type()),
                catalog.status(TransactionStatusEnum.COMPLETED), context.from(), context.to());
    }

    /**
     * @param context la operación, con la cuenta de origen, el tipo de movimiento y el día que se mide
     * @return suma de los montos completados del tipo indicado desde la cuenta en el día; cero si no hay ninguno
     */
    BigDecimal amount(LimitContext context) {
        return transactionRepository.sumCompletedFromAccount(context.account().id(), catalog.type(context.type()),
                catalog.status(TransactionStatusEnum.COMPLETED), context.from(), context.to());
    }
}
