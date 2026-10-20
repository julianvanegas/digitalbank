package com.udea.digitalbank.transactions.repository;

import com.udea.digitalbank.transactions.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

/** Lectura del catálogo {@code transaction_types}; {@code TransactionCatalog} lo carga al arrancar. */
public interface TransactionTypeRepository extends JpaRepository<TransactionType, Short> {
}
