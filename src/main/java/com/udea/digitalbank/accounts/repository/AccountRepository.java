package com.udea.digitalbank.accounts.repository;

import com.udea.digitalbank.accounts.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByAccountNumber(String accountNumber);

    // Las llaves que ya usan un prefijo, para saber en una sola consulta qué consecutivo está libre
    @Query("select a.accountKey from Account a where a.accountKey like concat(:prefix, '%')")
    List<String> findKeysByPrefix(@Param("prefix") String prefix);

    Optional<Account> findByAccountNumber(String accountNumber);

    // Un cliente tiene como máximo tres cuentas, así que la lista completa sirve tanto para
    // consultarlas como para decidir si queda cupo, sin una consulta de conteo aparte
    List<Account> findByCustomerIdOrderByCreatedAt(UUID customerId);
}
