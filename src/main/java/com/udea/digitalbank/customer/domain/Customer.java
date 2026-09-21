package com.udea.digitalbank.customer.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Solo datos personales: credenciales, estado y sesión viven en auth (users)
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    private static final short CUSTOMER_ROLE_ID = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Sin @ManyToOne hacia auth para mantener los módulos desacoplados
    @Column(nullable = false, unique = true)
    private Long userId;

    // Junto con userId forma la FK compuesta que impide que un ADMIN tenga fila aquí
    @Column(nullable = false, updatable = false)
    private Short roleId = CUSTOMER_ROLE_ID;

    @Column(nullable = false, length = 100)
    private String firstNames;

    @Column(nullable = false, length = 100)
    private String lastNames;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "document_type_id", nullable = false)
    private DocumentType documentType;

    @Column(nullable = false, unique = true)
    private String documentNumber;

    @Column(nullable = false, length = 16)
    private String phone;

    @Column(nullable = false)
    private LocalDate birthDate;

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
