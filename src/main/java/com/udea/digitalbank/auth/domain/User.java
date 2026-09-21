package com.udea.digitalbank.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Siempre en minúsculas (ver setEmail); la base lo garantiza con ck_users_email_lowercase
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false)
    private String passwordHash;        // hash BCrypt tal cual, nunca la contraseña

    @Column(nullable = false)
    private LocalDateTime passwordChangedAt;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private UserStatus status;

    @Column(nullable = false)
    private int failedAttempts = 0;

    private LocalDateTime lastLoginAt;  // nulo hasta el primer login

    // Solo cambia si el estado cambia de verdad. Sin él, reactivar a un cliente inactivo se
    // deshacía esa misma noche, porque su last_login_at seguía siendo antiguo.
    @Column(nullable = false)
    private LocalDateTime statusChangedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Único punto de escritura del email: nadie puede guardarlo sin normalizar
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
