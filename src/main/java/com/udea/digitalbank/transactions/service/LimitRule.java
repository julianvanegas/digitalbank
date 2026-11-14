package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.shared.exception.transactions.TransactionLimitExceededException;
import com.udea.digitalbank.transactions.domain.TransactionLimit;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Una regla de tope: una comprobación independiente que {@link TransactionLimitService} aplica a la operación de un
 * cliente. Cada tope es una implementación, así que agregar uno nuevo es una clase y no otra rama en el servicio. Las
 * reglas se aplican en el orden de su {@code @Order}, de la menos costosa (sin consultar la base) a la más costosa.
 */
interface LimitRule {

    /** Moneda en la que están expresados los montos. */
    String CURRENCY = "COP";

    /**
     * @param limit tope de la combinación de tipo de movimiento y tipo de cuenta
     * @return {@code true} si este tope está definido; un máximo nulo significa que no existe y la regla no se evalúa
     */
    boolean appliesTo(TransactionLimit limit);

    /**
     * Comprueba la operación contra este tope.
     *
     * @param context la operación y el tope que le aplica
     * @throws TransactionLimitExceededException si la operación lo supera
     */
    void check(LimitContext context);

    /** Un monto para mostrar en un mensaje: dos decimales y la moneda. */
    static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + CURRENCY;
    }
}
