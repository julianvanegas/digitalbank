package com.udea.digitalbank.transactions.repository;

import com.udea.digitalbank.transactions.domain.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionStatusRepository extends JpaRepository<TransactionStatus, Short> {
}
