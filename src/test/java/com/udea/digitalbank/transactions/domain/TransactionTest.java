package com.udea.digitalbank.transactions.domain;

import com.udea.digitalbank.shared.exception.transactions.TransactionNotPendingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static com.udea.digitalbank.transactions.TransactionFixtures.REFERENCE;
import static com.udea.digitalbank.transactions.TransactionFixtures.status;
import static com.udea.digitalbank.transactions.TransactionFixtures.transfer;
import static com.udea.digitalbank.transactions.TransactionFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las reglas del estado viven en la transacción: se prueban directamente, sin servicios ni mocks. */
@DisplayName("Transaction")
class TransactionTest {

    private static Transaction pending() {
        return transfer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "25.00", TransactionStatusEnum.PENDING);
    }

    @Test
    @DisplayName("No expone setters: el estado solo cambia con complete y reject")
    void noDeberiaExponerSetters() {
        assertThat(Arrays.stream(Transaction.class.getMethods()).map(m -> m.getName()))
                .noneMatch(name -> name.startsWith("set"));
    }

    @Nested
    @DisplayName("transfer")
    class Transfer {

        @Test
        @DisplayName("Nace PENDING, con los datos recibidos")
        void deberiaNacerPendiente() {
            UUID id = UUID.randomUUID();
            UUID user = UUID.randomUUID();
            UUID sender = UUID.randomUUID();
            UUID receiver = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            Transaction transaction = Transaction.transfer(id, user, sender, receiver, new BigDecimal("10.00"), REFERENCE,
                    type(TransactionTypeEnum.TRANSFER), status(TransactionStatusEnum.PENDING), now);

            assertThat(transaction.is(TransactionStatusEnum.PENDING)).isTrue();
            assertThat(transaction.getId()).isEqualTo(id);
            assertThat(transaction.isNew()).isTrue();
            assertThat(transaction.getUserOriginatorId()).isEqualTo(user);
            assertThat(transaction.getSenderAccountId()).isEqualTo(sender);
            assertThat(transaction.getReceiverAccountId()).isEqualTo(receiver);
            assertThat(transaction.getAmount()).isEqualByComparingTo("10.00");
            assertThat(transaction.getReference()).isEqualTo(REFERENCE);
            assertThat(transaction.getCreatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("Rechaza un estado inicial que no sea PENDING")
        void deberiaExigirElEstadoPending() {
            assertThatThrownBy(() -> Transaction.transfer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    BigDecimal.TEN, REFERENCE, type(TransactionTypeEnum.TRANSFER),
                    status(TransactionStatusEnum.COMPLETED), LocalDateTime.now()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Rechaza un tipo que no sea TRANSFER")
        void deberiaExigirElTipoTransfer() {
            assertThatThrownBy(() -> Transaction.transfer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    BigDecimal.TEN, REFERENCE, type(TransactionTypeEnum.DEPOSIT),
                    status(TransactionStatusEnum.PENDING), LocalDateTime.now()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("complete y reject")
    class Transitions {

        @Test
        @DisplayName("complete - una PENDING pasa a COMPLETED")
        void deberiaCompletarUnaPendiente() {
            Transaction transaction = pending();

            transaction.complete(status(TransactionStatusEnum.COMPLETED));

            assertThat(transaction.is(TransactionStatusEnum.COMPLETED)).isTrue();
        }

        @Test
        @DisplayName("reject - una PENDING pasa a REJECTED")
        void deberiaRechazarUnaPendiente() {
            Transaction transaction = pending();

            transaction.reject(status(TransactionStatusEnum.REJECTED));

            assertThat(transaction.is(TransactionStatusEnum.REJECTED)).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = TransactionStatusEnum.class, names = {"COMPLETED", "REJECTED", "REVERSED"})
        @DisplayName("complete y reject - solo parten de PENDING: cualquier otro estado se rechaza")
        void noDeberiaCambiarUnaTransaccionQueNoEstaPendiente(TransactionStatusEnum current) {
            Transaction transaction = transfer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "25.00", current);

            assertThatThrownBy(() -> transaction.complete(status(TransactionStatusEnum.COMPLETED)))
                    .isInstanceOf(TransactionNotPendingException.class);
            assertThatThrownBy(() -> transaction.reject(status(TransactionStatusEnum.REJECTED)))
                    .isInstanceOf(TransactionNotPendingException.class);
            assertThat(transaction.is(current)).isTrue();
        }

        @Test
        @DisplayName("Exige el estado del catálogo que corresponde al cambio pedido")
        void deberiaExigirElEstadoDelCambio() {
            Transaction transaction = pending();

            assertThatThrownBy(() -> transaction.complete(status(TransactionStatusEnum.REJECTED)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> transaction.reject(null)).isInstanceOf(IllegalArgumentException.class);
            assertThat(transaction.is(TransactionStatusEnum.PENDING)).isTrue();
        }
    }
}
