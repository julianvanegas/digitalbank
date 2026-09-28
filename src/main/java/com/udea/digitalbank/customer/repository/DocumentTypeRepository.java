package com.udea.digitalbank.customer.repository;

import com.udea.digitalbank.customer.domain.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Short> {
}
