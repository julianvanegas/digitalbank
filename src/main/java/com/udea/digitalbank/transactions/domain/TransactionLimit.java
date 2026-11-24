package com.udea.digitalbank.transactions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

/**
 * Topes de un tipo de movimiento para un tipo de cuenta. Es de solo lectura: la migración los define y esta entidad
 * no los modifica; la aplicación solo los consulta y los hace cumplir.
 *
 * <p>Un máximo {@code null} significa que ese tope no existe para la combinación: las transferencias, por ejemplo, solo
 * tienen acumulado diario. El tipo de cuenta se guarda por código y no por la entidad de {@code accounts}, para que
 * {@code transactions} siga sin depender de ella.
 */
@Entity
@Immutable
@Table(name = "transaction_limits")
@Getter
@NoArgsConstructor
public class TransactionLimit {

    @Id
    private Short id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "transaction_type_id", nullable = false)
    private TransactionType type;

    /** Código del tipo de cuenta, por ejemplo {@code "SAVINGS"}. */
    @Column(nullable = false, length = 30)
    private String accountTypeCode;

    /** Cantidad máxima de operaciones completadas por día y cuenta; {@code null} si no hay cupo. */
    private Integer maxDailyCount;

    /** Monto máximo de una sola operación; {@code null} si no hay tope por operación. */
    @Column(precision = 19, scale = 2)
    private BigDecimal maxPerTransaction;

    /** Monto máximo acumulado por día y cuenta; {@code null} si no hay acumulado. */
    @Column(precision = 19, scale = 2)
    private BigDecimal maxDailyAmount;

    /** @return {@code true} si hay tope por operación. */
    public boolean hasPerTransactionLimit() {
        return maxPerTransaction != null;
    }

    /** @return {@code true} si hay cupo de operaciones diarias. */
    public boolean hasDailyCountLimit() {
        return maxDailyCount != null;
    }

    /** @return {@code true} si hay acumulado diario. */
    public boolean hasDailyAmountLimit() {
        return maxDailyAmount != null;
    }

    /** Exige {@link #hasPerTransactionLimit()}. @return si el monto supera el máximo por operación. */
    public boolean exceedsPerTransaction(BigDecimal amount) {
        return amount.compareTo(maxPerTransaction) > 0;
    }

    /** Exige {@link #hasDailyCountLimit()}. @return si ya se hicieron todas las operaciones permitidas en el día. */
    public boolean reachedDailyCount(long done) {
        return done >= maxDailyCount;
    }

    /** Exige {@link #hasDailyAmountLimit()}. @return si sumar el monto a lo ya usado pasa el acumulado diario. */
    public boolean exceedsDailyAmount(BigDecimal used, BigDecimal amount) {
        return used.add(amount).compareTo(maxDailyAmount) > 0;
    }

    /** Exige {@link #hasDailyAmountLimit()}. @return lo que queda del acumulado diario, nunca negativo. */
    public BigDecimal remainingDailyAmount(BigDecimal used) {
        return maxDailyAmount.subtract(used).max(BigDecimal.ZERO);
    }
}
