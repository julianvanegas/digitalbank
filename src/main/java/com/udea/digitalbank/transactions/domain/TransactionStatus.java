package com.udea.digitalbank.transactions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Fila del catálogo {@code status_transaction}. Es de solo lectura: los estados los define la migración y
 * {@link TransactionStatusEnum} es su contrato en el código.
 */
@Entity
@Immutable
@Table(name = "status_transaction")
@Getter
@NoArgsConstructor
public class TransactionStatus {

    @Id
    private Short id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    /** @return {@code true} si esta fila corresponde al estado indicado. */
    public boolean is(TransactionStatusEnum expected) {
        return code.equals(expected.name());
    }
}
