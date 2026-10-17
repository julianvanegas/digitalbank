package com.udea.digitalbank.accounts.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Una acción del historial de una cuenta: se bloqueó, se desbloqueó o se cerró, con su motivo, quién la hizo y
 * cuándo. Es historial: solo se inserta, nunca se modifica. El estado actual de la cuenta vive en
 * accounts.status_id.
 */
@Entity
@Table(name = "account_actions")
@Getter
@NoArgsConstructor
public class AccountAction {

    public static final int MAX_REASON_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID accountId;

    // Obligatorio en todas las acciones. En un cierre dice quién lo hizo: el cliente o un administrador
    @Column(nullable = false, updatable = false, length = MAX_REASON_LENGTH)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private AccountActionType action;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    // Usuario que hizo la acción, tal como viaja en el JWT: un administrador, o el titular si cerró su cuenta
    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    public static AccountAction blocked(UUID accountId, String reason, UUID createdBy) {
        return of(accountId, reason, AccountActionType.BLOCKED, createdBy);
    }

    public static AccountAction unlocked(UUID accountId, String reason, UUID createdBy) {
        return of(accountId, reason, AccountActionType.UNLOCKED, createdBy);
    }

    public static AccountAction closed(UUID accountId, String reason, UUID createdBy) {
        return of(accountId, reason, AccountActionType.CLOSED, createdBy);
    }

    private static AccountAction of(UUID accountId, String reason, AccountActionType action, UUID createdBy) {
        AccountAction entry = new AccountAction();
        entry.accountId = accountId;
        entry.reason = reason;
        entry.action = action;
        entry.createdBy = createdBy;
        return entry;
    }
}
