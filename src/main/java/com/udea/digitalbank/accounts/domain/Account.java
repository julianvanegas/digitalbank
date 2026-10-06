package com.udea.digitalbank.accounts.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// Cuenta de ahorros de un cliente. Los datos del titular viven en customer: aquí solo su id.
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account {

    public static final BigDecimal INITIAL_BALANCE = new BigDecimal("0.00");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Identificador público de la cuenta: 11 dígitos, único e inmutable
    @Column(nullable = false, unique = true, updatable = false, length = 11)
    private String accountNumber;

    // Sin @ManyToOne hacia customer para mantener los módulos desacoplados
    @Column(nullable = false, updatable = false)
    private UUID customerId;

    // Posición dentro del cupo del titular (1 a 3): junto con el UNIQUE (customer_id, account_index)
    // de la base es lo que impide una cuarta cuenta incluso con dos aperturas simultáneas
    @Column(nullable = false, updatable = false)
    private Short accountIndex;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private AccountStatus status;

    // Con la escala de la columna desde el principio: así el saldo se serializa igual
    // recién creada la cuenta ("0.00") que leída de la base, y no como "0"
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = INITIAL_BALANCE;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
