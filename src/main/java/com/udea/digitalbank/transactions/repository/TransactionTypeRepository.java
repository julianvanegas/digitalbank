package com.udea.digitalbank.transactions.repository;

import com.udea.digitalbank.transactions.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionTypeRepository extends JpaRepository<TransactionType, Short> {
}
