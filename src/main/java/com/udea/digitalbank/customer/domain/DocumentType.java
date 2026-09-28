package com.udea.digitalbank.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

// Catálogo de solo lectura de los documentos con los que se identifica un cliente
@Entity
@Immutable
@Table(name = "document_type")
@Getter
@NoArgsConstructor
public class DocumentType {

    @Id
    private Short id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;
}
