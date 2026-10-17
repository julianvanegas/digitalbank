package com.udea.digitalbank.transactions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

// Catálogo de solo lectura de los tipos de movimiento
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

    public boolean is(TransactionTypeEnum expected) {
        return code.equals(expected.name());
    }
}
