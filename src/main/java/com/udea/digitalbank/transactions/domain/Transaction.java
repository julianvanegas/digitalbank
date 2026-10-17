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
 * Un movimiento de dinero. Las cuentas se guardan por id, sin @ManyToOne hacia accounts, para mantener los módulos
 * desacoplados. Es dueña de sus reglas: el estado no tiene setter y solo cambia con {@link #complete} y
 * {@link #reject}, que exigen partir de PENDING. La transferencia nace con {@link #transfer}.
 */
@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor
public class Transaction implements Persistable<UUID> {

    public static final int MAX_REFERENCE_LENGTH = 64;

    // Se asigna al crear la transferencia y no al guardarla: la referencia (el comprobante) se deriva de este id
    @Id
    private UUID id;

    // Quien ordena el movimiento, tal como viaja en el token
    @Column(nullable = false, updatable = false)
    private UUID userOriginatorId;

    // Nulo en un depósito
    @Column(updatable = false)
    private UUID senderAccountId;

    // Nulo en un retiro
    @Column(updatable = false)
    private UUID receiverAccountId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "transaction_type_id", nullable = false, updatable = false)
    private TransactionType type;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private TransactionStatus status;

    // Siempre positivo: el sentido del movimiento lo dan las cuentas, no el signo
    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // Comprobante de la operación, único
    @Column(nullable = false, updatable = false, unique = true, length = MAX_REFERENCE_LENGTH)
    private String reference;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Con el id asignado de antemano, Spring Data no puede saber por el id si la fila es nueva: lo dice esta marca,
    // que se apaga al guardarla o al leerla. Así save() hace un INSERT directo y no un SELECT previo con merge
    @Transient
    @Getter(AccessLevel.NONE)
    private boolean persisted;

    /**
     * Una transferencia nueva: queda PENDING hasta que el segundo factor la autorice. El id lo trae quien la crea
     * porque la referencia se calcula a partir de él antes de guardarla.
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

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        persisted = true;
    }

    public boolean is(TransactionStatusEnum expected) {
        return status.is(expected);
    }

    /** El dinero ya se movió: solo una transacción PENDING puede completarse. */
    public void complete(TransactionStatus completedStatus) {
        requirePending("completar");
        requireStatus(completedStatus, TransactionStatusEnum.COMPLETED);
        status = completedStatus;
    }

    /** Se cancela sin mover dinero: solo una transacción PENDING puede rechazarse. */
    public void reject(TransactionStatus rejectedStatus) {
        requirePending("rechazar");
        requireStatus(rejectedStatus, TransactionStatusEnum.REJECTED);
        status = rejectedStatus;
    }

    private void requirePending(String action) {
        if (!is(TransactionStatusEnum.PENDING)) {
            throw new TransactionNotPendingException("No se puede " + action + " la transacción " + reference
                    + ": está en estado " + status.getCode());
        }
    }

    // El estado que se pasa debe ser el del cambio que se pide: evita dejar la transacción en un estado equivocado
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
