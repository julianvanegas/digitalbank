package com.udea.digitalbank.customer.repository;

import com.udea.digitalbank.customer.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByUserId(Long userId);
    boolean existsByDocumentNumber(String documentNumber);
}
