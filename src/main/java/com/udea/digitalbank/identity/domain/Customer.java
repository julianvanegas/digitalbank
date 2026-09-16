package com.udea.digitalbank.identity.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String documentType;       // "CC", "CE", "PASSPORT"

    @Column(nullable = false, unique = true)
    private String documentNumber;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private String passwordHash;        // nunca el password en texto plano

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerStatus status;

    @Column(nullable = false)
    private int failedAttempts = 0;     // para el bloqueo tras 3 intentos

    private LocalDateTime lastLogin;    // para el estado INACTIVE tras 12 meses

    @Column(unique = true)
    private String currentTokenId;      // jti del último JWT emitido (sesión única)

    private String twoFactorCode;                  // código 2FA vigente
    private LocalDateTime twoFactorCodeExpiration;  // expiración del código 2FA

    @Column(nullable = false)
    private int twoFactorFailedAttempts = 0;

    private String passwordResetToken;                   // token de recuperación de contraseña
    private LocalDateTime passwordResetTokenExpiration;   // expiración del token de recuperación
}