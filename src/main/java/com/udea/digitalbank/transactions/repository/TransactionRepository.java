package com.udea.digitalbank.transactions.repository;

import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatus;
import com.udea.digitalbank.transactions.domain.TransactionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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
     * Suma de los montos de las transacciones del tipo indicado que ya se completaron y salieron de la cuenta en el
     * intervalo {@code [from, to)}. Es el consumo del día contra el tope acumulado.
     *
     * @return la suma, o cero si no hay ninguna
     */
    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.senderAccountId = :accountId and t.type = :type and t.status = :completed
              and t.createdAt >= :from and t.createdAt < :to
            """)
    BigDecimal sumCompletedFromAccount(@Param("accountId") UUID accountId, @Param("type") TransactionType type,
                                       @Param("completed") TransactionStatus completed,
                                       @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Cantidad de transacciones del tipo indicado ya completadas desde la cuenta en {@code [from, to)}. */
    @Query("""
            select count(t) from Transaction t
            where t.senderAccountId = :accountId and t.type = :type and t.status = :completed
              and t.createdAt >= :from and t.createdAt < :to
            """)
    long countCompletedFromAccount(@Param("accountId") UUID accountId, @Param("type") TransactionType type,
                                   @Param("completed") TransactionStatus completed,
                                   @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

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
