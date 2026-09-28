package com.udea.digitalbank.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Duration;

@Entity
@Immutable
@Table(name = "challenge_purposes")
@Getter
@NoArgsConstructor
public class ChallengePurpose {

    @Id
    private Short id;

    // Se mantiene como "purpose" por decisión de diseño del modelo, aunque los demás catálogos usan "code"
    @Column(nullable = false, unique = true, length = 30)
    private String purpose;

    @Column(nullable = false)
    private Duration ttl;

    @Column(nullable = false)
    private int maxAttempts;
}
