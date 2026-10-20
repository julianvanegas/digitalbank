package com.udea.digitalbank.transactions.repository;

import com.udea.digitalbank.transactions.domain.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/** Lectura del catálogo {@code status_transaction}; {@code TransactionCatalog} lo carga al arrancar. */
public interface TransactionStatusRepository extends JpaRepository<TransactionStatus, Short> {
}
