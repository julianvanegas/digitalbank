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
@Table(name = "category")
@Getter
@NoArgsConstructor
public class Category {

    @Id
    private Short id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;
}
