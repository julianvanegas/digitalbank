package com.udea.digitalbank.accounts.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registro de un bloqueo de cuenta. Es historial: se inserta y no se modifica, así que conserva el
 * motivo de cada bloqueo y no solo el del último.
 */
@Entity
@Table(name = "account_blocks")
@Getter
@Setter
@NoArgsConstructor
public class AccountBlock {

    public static final int MAX_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID accountId;

    @Column(nullable = false, updatable = false, length = MAX_REASON_LENGTH)
    private String reason;

    // Usuario administrador que ordenó el bloqueo, tal como viaja en el JWT
    @Column(nullable = false, updatable = false)
    private UUID blockedByUserId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime blockedAt;

    @PrePersist
    void onCreate() {
        blockedAt = LocalDateTime.now();
    }
}
