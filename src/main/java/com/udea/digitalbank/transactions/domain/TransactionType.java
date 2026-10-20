package com.udea.digitalbank.transactions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Fila del catálogo {@code transaction_types}. Es de solo lectura: los tipos los define la migración y
 * {@link TransactionTypeEnum} es su contrato en el código.
 */
@Entity
@Immutable
@Table(name = "transaction_types")
@Getter
@NoArgsConstructor
public class TransactionType {

    @Id
    private Short id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    /** @return {@code true} si esta fila corresponde al tipo indicado. */
    public boolean is(TransactionTypeEnum expected) {
        return code.equals(expected.name());
    }
}
