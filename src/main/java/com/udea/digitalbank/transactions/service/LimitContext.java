package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.transactions.domain.TransactionLimit;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lo que una {@link LimitRule} necesita para comprobar una operación: el tope que aplica, la cuenta de origen, el tipo
 * y el monto, y el día calendario sobre el que se mide el consumo.
 *
 * @param limit   tope del tipo de movimiento para el tipo de cuenta
 * @param account cuenta de la que sale el dinero
 * @param type    tipo de movimiento
 * @param amount  monto de la operación
 * @param from    inicio del día (inclusive)
 * @param to      inicio del día siguiente (exclusive)
 */
record LimitContext(TransactionLimit limit, AccountView account, TransactionTypeEnum type, BigDecimal amount,
                    LocalDateTime from, LocalDateTime to) {
}
