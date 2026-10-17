package com.udea.digitalbank.accounts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Catálogo de solo lectura de los tipos de cuenta: determina qué límites aplican a cada una. */
@Entity
@Immutable
@Table(name = "account_types")
@Getter
@NoArgsConstructor
public class AccountType {

    @Id
    private Short id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;
}
