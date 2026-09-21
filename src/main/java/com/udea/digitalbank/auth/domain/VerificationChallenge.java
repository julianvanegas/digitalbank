package com.udea.digitalbank.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "verification_challenge")
@Getter
@Setter
@NoArgsConstructor
public class VerificationChallenge {

    @Id
    private UUID id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "purpose_id", nullable = false)
    private ChallengePurpose purpose;

    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int failedAttempts = 0;

    // Momento de emisión: no se guarda, se deriva de la vigencia del propósito
    public LocalDateTime issuedAt() {
        return expiresAt.minusMinutes(purpose.getTtlMinutes());
    }
}
