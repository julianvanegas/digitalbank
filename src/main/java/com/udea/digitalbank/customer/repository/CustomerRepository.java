package com.udea.digitalbank.customer.repository;

import com.udea.digitalbank.customer.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByUserId(UUID userId);
    boolean existsByDocumentTypeIdAndDocumentNumber(Short documentTypeId, String documentNumber);
}
