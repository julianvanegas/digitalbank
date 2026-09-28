package com.udea.digitalbank.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "role")
@Getter
@NoArgsConstructor
public class Role {

    @Id
    private Short id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;
}
