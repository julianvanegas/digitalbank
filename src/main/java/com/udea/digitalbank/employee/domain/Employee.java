package com.udea.digitalbank.employee.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Solo datos personales: credenciales, estado y sesión viven en auth (users). */
@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    private static final short ADMIN_ROLE_ID = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Sin {@code @ManyToOne} hacia auth para mantener los módulos desacoplados. */
    @Column(nullable = false, unique = true)
    private UUID userId;

    /** Junto con userId forma la FK compuesta que impide que un CUSTOMER tenga fila aquí. */
    @Column(nullable = false, updatable = false)
    private Short roleId = ADMIN_ROLE_ID;

    @Column(nullable = false, length = 100)
    private String firstNames;

    @Column(nullable = false, length = 100)
    private String lastNames;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
