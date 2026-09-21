package com.udea.digitalbank.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Única sesión vigente de cada usuario: un login nuevo sobrescribe la fila
@Entity
@Table(name = "auth_session")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AuthSession {

    @Id
    private Long userId;

    @Column(nullable = false, unique = true)
    private String jti;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}
