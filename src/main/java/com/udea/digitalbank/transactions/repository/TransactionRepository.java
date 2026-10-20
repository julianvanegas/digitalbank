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

/**
 * Acceso a las transacciones. Los métodos {@code ...ForUpdate} toman un bloqueo pesimista de escritura
 * ({@code SELECT ... FOR UPDATE}) que se mantiene hasta que termina la transacción de base de datos.
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** Busca por comprobante sin bloquear la fila; para consultas de solo lectura. */
    Optional<Transaction> findByReference(String reference);

    /**
     * Busca por comprobante y bloquea la fila. Dos confirmaciones simultáneas de la misma transferencia se
     * serializan: la segunda la encuentra ya completada en lugar de intentar mover el dinero otra vez.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.reference = :reference")
    Optional<Transaction> findByReferenceForUpdate(@Param("reference") String reference);

    /**
     * Transacciones {@code PENDING} del usuario, con la fila bloqueada. Se usa al iniciar otra transferencia
     * para cancelar las anteriores, porque su código de verificación deja de ser válido.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.userOriginatorId = :userId and t.status = :pending")
    List<Transaction> findPendingOfUserForUpdate(@Param("userId") UUID userId,
                                                 @Param("pending") TransactionStatus pending);

    /**
     * Rechaza, en una sola sentencia, las transacciones {@code PENDING} creadas antes de {@code cutoff}. El
     * {@code UPDATE} solo afecta filas que sigan {@code PENDING} al ejecutarse, así que no sobrescribe una
     * confirmación ya completada.
     *
     * @param cutoff   instante anterior al cual una transacción pendiente se considera vencida
     * @param pending  fila {@code PENDING} del catálogo de estados
     * @param rejected fila {@code REJECTED} del catálogo de estados
     * @return cantidad de transacciones rechazadas
     */
    @Modifying
    @Query("""
            update Transaction t set t.status = :rejected
            where t.status = :pending and t.createdAt < :cutoff
            """)
    int rejectPendingCreatedBefore(@Param("cutoff") LocalDateTime cutoff,
                                   @Param("pending") TransactionStatus pending,
                                   @Param("rejected") TransactionStatus rejected);
}
