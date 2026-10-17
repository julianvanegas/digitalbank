package com.udea.digitalbank.accounts.repository;

import com.udea.digitalbank.accounts.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    // Siguiente valor de la secuencia de números de cuenta (V10): único y atómico, sin mirar la tabla
    @Query(value = "select nextval('account_number_seq')", nativeQuery = true)
    long nextAccountNumberValue();

    // Las llaves que ya usan un prefijo, para saber en una sola consulta qué consecutivo está libre
    @Query("select a.accountKey from Account a where a.accountKey like concat(:prefix, '%')")
    List<String> findKeysByPrefix(@Param("prefix") String prefix);

    Optional<Account> findByAccountNumber(String accountNumber);

    // Misma búsqueda, pero bloquea la fila hasta el fin de la transacción (SELECT ... FOR UPDATE): quien
    // cambia el estado de la cuenta espera a que termine el cambio anterior y lo ve ya aplicado
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

    // Varias cuentas con la fila bloqueada, siempre en el mismo orden (por id): dos transferencias cruzadas entre las
    // mismas cuentas piden los bloqueos en el mismo orden y se turnan en vez de quedar en un interbloqueo
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id")
    List<Account> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);

    // Un cliente tiene como máximo tres cuentas, así que la lista completa sirve tanto para
    // consultarlas como para decidir si queda cupo, sin una consulta de conteo aparte
    List<Account> findByCustomerIdOrderByCreatedAt(UUID customerId);
}
