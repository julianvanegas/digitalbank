package com.udea.digitalbank.transactions.domain;

import com.udea.digitalbank.shared.exception.transactions.TransactionNotPendingException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Movimiento de dinero entre cuentas (transferencia, depósito o retiro). La creación está disponible solo para
 * transferencias, mediante {@link #transfer}.
 *
 * <p>Las cuentas se referencian por id y no con {@code @ManyToOne}: así {@code transactions} no depende de las
 * entidades de {@code accounts} y los módulos siguen desacoplados.
 *
 * <p>La entidad protege sus propias reglas. No tiene setters: el estado solo cambia con {@link #complete} y
 * {@link #reject}, y ambos exigen que la transacción esté {@code PENDING}.
 *
 * <p>Implementa {@link Persistable} porque el identificador se asigna antes de guardar; ver {@link #isNew()}.
 */
@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor
public class Transaction implements Persistable<UUID> {

    /** Longitud máxima de la referencia; coincide con la columna {@code reference}. */
    public static final int MAX_REFERENCE_LENGTH = 64;

    /**
     * Se asigna al crear la transacción y no al guardarla, porque la referencia (el comprobante) se deriva de este
     * valor.
     */
    @Id
    private UUID id;

    /** Usuario que ordena el movimiento, tal como viaja en el token de autenticación. */
    @Column(nullable = false, updatable = false)
    private UUID userOriginatorId;

    /** Cuenta que entrega el dinero; es {@code null} en un depósito. */
    @Column(updatable = false)
    private UUID senderAccountId;

    /** Cuenta que recibe el dinero; es {@code null} en un retiro. */
    @Column(updatable = false)
    private UUID receiverAccountId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "transaction_type_id", nullable = false, updatable = false)
    private TransactionType type;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private TransactionStatus status;

    /** Monto siempre positivo: el sentido del movimiento lo determinan las cuentas, no el signo. */
    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Comprobante de la operación, que se entrega al ordenante. Es único. */
    @Column(nullable = false, updatable = false, unique = true, length = MAX_REFERENCE_LENGTH)
    private String reference;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Indica si la entidad ya existe en la base. No se persiste: se activa al guardar o cargar la fila
     * ({@link #markPersisted()}) y la lee {@link #isNew()}.
     */
    @Transient
    @Getter(AccessLevel.NONE)
    private boolean persisted;

    /**
     * Crea una transferencia en estado {@code PENDING}, a la espera de que el segundo factor la autorice.
     *
     * @param id               identificador de la transacción; lo aporta quien la crea porque la referencia se
     *                         calcula a partir de él antes de guardarla
     * @param userOriginatorId usuario que ordena la transferencia
     * @param senderAccountId  cuenta de origen
     * @param receiverAccountId cuenta de destino
     * @param amount           monto positivo, con la escala de la columna ({@code NUMERIC(19,2)})
     * @param reference        comprobante de la operación
     * @param transferType     fila {@code TRANSFER} del catálogo de tipos
     * @param pendingStatus    fila {@code PENDING} del catálogo de estados
     * @param createdAt        instante de creación
     * @return la transacción, aún sin guardar
     * @throws IllegalArgumentException si {@code transferType} no es {@code TRANSFER} o {@code pendingStatus} no es
     *                                  {@code PENDING}
     */
    public static Transaction transfer(UUID id, UUID userOriginatorId, UUID senderAccountId, UUID receiverAccountId,
                                       BigDecimal amount, String reference, TransactionType transferType,
                                       TransactionStatus pendingStatus, LocalDateTime createdAt) {
        requireType(transferType, TransactionTypeEnum.TRANSFER);
        requireStatus(pendingStatus, TransactionStatusEnum.PENDING);
        Transaction transaction = new Transaction();
        transaction.id = id;
        transaction.userOriginatorId = userOriginatorId;
        transaction.senderAccountId = senderAccountId;
        transaction.receiverAccountId = receiverAccountId;
        transaction.amount = amount;
        transaction.reference = reference;
        transaction.type = transferType;
        transaction.status = pendingStatus;
        transaction.createdAt = createdAt;
        return transaction;
    }

    /**
     * Informa a Spring Data si la fila aún no existe. Con el id asignado de antemano no puede deducirlo del propio
     * id; con esta marca {@code save} hace un {@code INSERT} directo, sin el {@code SELECT} previo de un {@code merge}.
     */
    @Override
    public boolean isNew() {
        return !persisted;
    }

    /** Marca la entidad como existente en la base después de guardarla o cargarla. */
    @PostLoad
    @PostPersist
    void markPersisted() {
        persisted = true;
    }

    /** @return {@code true} si la transacción está en el estado indicado. */
    public boolean is(TransactionStatusEnum expected) {
        return status.is(expected);
    }

    /**
     * Marca la transacción como completada. Se llama cuando el dinero ya se ha movido.
     *
     * @param completedStatus fila {@code COMPLETED} del catálogo de estados
     * @throws TransactionNotPendingException si la transacción no está {@code PENDING}
     * @throws IllegalArgumentException       si {@code completedStatus} no es {@code COMPLETED}
     */
    public void complete(TransactionStatus completedStatus) {
        requirePending("completar");
        requireStatus(completedStatus, TransactionStatusEnum.COMPLETED);
        status = completedStatus;
    }

    /**
     * Cancela la transacción sin mover dinero.
     *
     * @param rejectedStatus fila {@code REJECTED} del catálogo de estados
     * @throws TransactionNotPendingException si la transacción no está {@code PENDING}
     * @throws IllegalArgumentException       si {@code rejectedStatus} no es {@code REJECTED}
     */
    public void reject(TransactionStatus rejectedStatus) {
        requirePending("rechazar");
        requireStatus(rejectedStatus, TransactionStatusEnum.REJECTED);
        status = rejectedStatus;
    }

    // Solo una transacción pendiente admite un cambio de estado
    private void requirePending(String action) {
        if (!is(TransactionStatusEnum.PENDING)) {
            throw new TransactionNotPendingException("No se puede " + action + " la transacción " + reference
                    + ": está en estado " + status.getCode());
        }
    }

    // Garantiza que el estado recibido corresponde al cambio solicitado, para no dejar la transacción en uno equivocado
    private static void requireStatus(TransactionStatus target, TransactionStatusEnum expected) {
        if (target == null || !target.is(expected)) {
            throw new IllegalArgumentException("Se esperaba el estado " + expected + " del catálogo");
        }
    }

    private static void requireType(TransactionType target, TransactionTypeEnum expected) {
        if (target == null || !target.is(expected)) {
            throw new IllegalArgumentException("Se esperaba el tipo " + expected + " del catálogo");
        }
    }
}
