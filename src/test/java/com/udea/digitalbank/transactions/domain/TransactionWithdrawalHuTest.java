package com.udea.digitalbank.transactions.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.udea.digitalbank.transactions.TransactionFixtures.status;
import static com.udea.digitalbank.transactions.TransactionFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de {@link Transaction#withdrawal}, la fábrica de los retiros de HU19. Siguen las de las fábricas hermanas en
 * TransactionTest: nace con los datos recibidos y exige el tipo y el estado del catálogo que le corresponden.
 */
@DisplayName("HU19 - Transaction.withdrawal")
class TransactionWithdrawalHuTest {

    private static final String REFERENCE = "RET-20261017-7Xk2Pq9RmZ4tNvB8cHdWeA";

    @Test
    @DisplayName("HU19 - Nace PENDING y de tipo WITHDRAWAL, con cuenta de origen y sin cuenta de destino")
    void deberiaNacerPendienteConSoloCuentaDeOrigen() {
        UUID sender = UUID.randomUUID();

        Transaction transaction = Transaction.withdrawal(UUID.randomUUID(), UUID.randomUUID(), sender,
                new BigDecimal("10.00"), REFERENCE, type(TransactionTypeEnum.WITHDRAWAL),
                status(TransactionStatusEnum.PENDING), LocalDateTime.now());

        assertThat(transaction.is(TransactionStatusEnum.PENDING)).isTrue();
        assertThat(transaction.getType().is(TransactionTypeEnum.WITHDRAWAL)).isTrue();
        assertThat(transaction.getSenderAccountId()).isEqualTo(sender);
        // ck_transactions_sides: un retiro tiene origen y no tiene destino
        assertThat(transaction.getReceiverAccountId()).isNull();
    }

    @Test
    @DisplayName("HU19 - Conserva el id, el ordenante, el monto, la referencia y la fecha que recibe")
    void deberiaConservarLosDatosRecibidos() {
        UUID id = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Transaction transaction = Transaction.withdrawal(id, user, UUID.randomUUID(), new BigDecimal("10.00"),
                REFERENCE, type(TransactionTypeEnum.WITHDRAWAL), status(TransactionStatusEnum.PENDING), now);

        assertThat(transaction.getId()).isEqualTo(id);
        assertThat(transaction.isNew()).isTrue();
        assertThat(transaction.getUserOriginatorId()).isEqualTo(user);
        assertThat(transaction.getAmount()).isEqualByComparingTo("10.00");
        assertThat(transaction.getReference()).isEqualTo(REFERENCE);
        assertThat(transaction.getCreatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("HU19 - Rechaza un estado inicial que no sea PENDING")
    void deberiaExigirElEstadoPending() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        TransactionType withdrawalType = type(TransactionTypeEnum.WITHDRAWAL);
        TransactionStatus completedStatus = status(TransactionStatusEnum.COMPLETED);
        LocalDateTime now = LocalDateTime.now();

        assertThatThrownBy(() -> Transaction.withdrawal(id, userId, senderId, BigDecimal.TEN, REFERENCE,
                withdrawalType, completedStatus, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("HU19 - Rechaza un tipo que no sea WITHDRAWAL")
    void deberiaExigirElTipoWithdrawal() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        TransactionType transferType = type(TransactionTypeEnum.TRANSFER);
        TransactionStatus pendingStatus = status(TransactionStatusEnum.PENDING);
        LocalDateTime now = LocalDateTime.now();

        assertThatThrownBy(() -> Transaction.withdrawal(id, userId, senderId, BigDecimal.TEN, REFERENCE,
                transferType, pendingStatus, now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
