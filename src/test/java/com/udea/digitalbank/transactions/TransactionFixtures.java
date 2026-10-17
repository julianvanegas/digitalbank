package com.udea.digitalbank.transactions;

import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatus;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.domain.TransactionType;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Datos de prueba compartidos por los tests del módulo transactions. */
public final class TransactionFixtures {

    public static final String SENDER_NUMBER = "100000000001";
    public static final String RECEIVER_NUMBER = "200000000002";
    public static final String REFERENCE = "TRF-20261017-7Xk2Pq9RmZ4tNvB8cHdWeA";

    private TransactionFixtures() {
    }

    public static TransactionStatus status(TransactionStatusEnum statusEnum) {
        TransactionStatus status = new TransactionStatus();
        ReflectionTestUtils.setField(status, "id", (short) (statusEnum.ordinal() + 1));
        ReflectionTestUtils.setField(status, "code", statusEnum.name());
        return status;
    }

    public static TransactionType type(TransactionTypeEnum typeEnum) {
        TransactionType type = new TransactionType();
        ReflectionTestUtils.setField(type, "id", (short) (typeEnum.ordinal() + 1));
        ReflectionTestUtils.setField(type, "code", typeEnum.name());
        return type;
    }

    /** Una transferencia de {@code amount} en el estado indicado. */
    public static Transaction transfer(UUID userId, UUID senderId, UUID receiverId, String amount,
                                       TransactionStatusEnum current) {
        Transaction transaction = Transaction.transfer(UUID.randomUUID(), userId, senderId, receiverId,
                new BigDecimal(amount), REFERENCE, type(TransactionTypeEnum.TRANSFER),
                status(TransactionStatusEnum.PENDING), LocalDateTime.now());
        ReflectionTestUtils.setField(transaction, "status", status(current));
        return transaction;
    }
}
