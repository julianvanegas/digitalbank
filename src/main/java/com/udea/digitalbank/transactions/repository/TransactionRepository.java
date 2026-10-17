package com.udea.digitalbank.transactions.repository;

import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByReference(String reference);

    // Bloquea la fila hasta el fin de la transacción: dos confirmaciones simultáneas de la misma transferencia se
    // turnan, y la segunda la ve ya completada en vez de intentar moverla de nuevo
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.reference = :reference")
    Optional<Transaction> findByReferenceForUpdate(@Param("reference") String reference);

    // Las transferencias que un usuario dejó sin confirmar, con la fila bloqueada: al iniciar otra, las anteriores
    // se cancelan porque su código ya no vale
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.userOriginatorId = :userId and t.status = :pending")
    List<Transaction> findPendingOfUserForUpdate(@Param("userId") UUID userId,
                                                 @Param("pending") TransactionStatus pending);

    // Barrido de las PENDING cuyo código ya venció. Una sola sentencia: solo mueve las que sigan PENDING al ejecutarla,
    // así que una confirmación que ganó la carrera no se pisa
    @Modifying
    @Query("""
            update Transaction t set t.status = :rejected
            where t.status = :pending and t.createdAt < :cutoff
            """)
    int rejectPendingCreatedBefore(@Param("cutoff") LocalDateTime cutoff,
                                   @Param("pending") TransactionStatus pending,
                                   @Param("rejected") TransactionStatus rejected);
}
