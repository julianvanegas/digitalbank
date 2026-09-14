package com.udea.digitalbank.identity.repository;

import com.udea.digitalbank.identity.domain.Customer;
import com.udea.digitalbank.identity.domain.CustomerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmail(String email);
//    Optional<Customer> findByDocumentNumber(String documentNumber);
    Optional<Customer> findByPasswordResetToken(String passwordResetToken);
    boolean existsByEmail(String email);
    boolean existsByDocumentNumber(String documentNumber);
    boolean existsByEmailAndIdNot(String email, Long id);
    List<Customer> findByStatusAndLastLoginBefore(CustomerStatus status, LocalDateTime cutoff);
}
