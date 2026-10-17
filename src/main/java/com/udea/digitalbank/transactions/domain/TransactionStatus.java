package com.udea.digitalbank.transactions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

// Catálogo de solo lectura de los estados de una transacción
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

    public boolean is(TransactionStatusEnum expected) {
        return code.equals(expected.name());
    }
}
