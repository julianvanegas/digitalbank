package com.udea.digitalbank.transactions.domain;

import com.udea.digitalbank.shared.exception.transactions.TransactionNotPendingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static com.udea.digitalbank.transactions.TransactionFixtures.REFERENCE;
import static com.udea.digitalbank.transactions.TransactionFixtures.status;
import static com.udea.digitalbank.transactions.TransactionFixtures.transfer;
import static com.udea.digitalbank.transactions.TransactionFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las reglas del estado viven en la transacción: se prueban directamente, sin servicios ni mocks. */
@DisplayName("Transaction")
class TransactionTest {

    private static Transaction pending() {
        return transfer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "25.00", TransactionStatusEnum.PENDING);
    }

    @Test
    @DisplayName("Transversal - isOrderedBy - Solo quien ordenó la transacción es su ordenante")
    void deberiaReconocerSoloAlOrdenante() {
        UUID orderingUser = UUID.randomUUID();
        Transaction transaction = transfer(orderingUser, UUID.randomUUID(), UUID.randomUUID(), "25.00",
                TransactionStatusEnum.PENDING);

        assertThat(transaction.isOrderedBy(orderingUser)).isTrue();
        assertThat(transaction.isOrderedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("HU21 - requireConfirmable - Una transacción PENDING admite confirmación")
    void deberiaAdmitirLaConfirmacionDeUnaPendiente() {
        assertThatCode(() -> pending().requireConfirmable()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"REJECTED,fue cancelada", "REVERSED,fue revertida", "COMPLETED,su estado es COMPLETED"})
    @DisplayName("HU21 CA03 - requireConfirmable - Una transacción que ya no está pendiente dice en qué quedó y pide iniciar otra")
    void deberiaRechazarLaConfirmacionDeUnaNoPendiente(TransactionStatusEnum current, String phrase) {
        Transaction transaction = transfer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "25.00", current);

        assertThatThrownBy(transaction::requireConfirmable)
                .isInstanceOf(TransactionNotPendingException.class)
                .hasMessageContaining(REFERENCE)
                .hasMessageContaining(phrase)
                .hasMessageContaining("Inicia una nueva");
    }

    @Test
    @DisplayName("Transversal - No expone setters: el estado solo cambia con complete y reject")
    void noDeberiaExponerSetters() {
        assertThat(Arrays.stream(Transaction.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("set"));
    }

    @Nested
    @DisplayName("HU20 - transfer")
    class Transfer {

        @Test
        @DisplayName("HU20 CA01 - Nace PENDING, con los datos recibidos")
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
        @DisplayName("Transversal - Rechaza un estado inicial que no sea PENDING")
        void deberiaExigirElEstadoPending() {
            UUID id = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();
            TransactionType transferType = type(TransactionTypeEnum.TRANSFER);
            TransactionStatus completedStatus = status(TransactionStatusEnum.COMPLETED);
            LocalDateTime now = LocalDateTime.now();
            assertThatThrownBy(() -> Transaction.transfer(id, userId, senderId, receiverId, BigDecimal.TEN, REFERENCE, transferType, completedStatus, now))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Transversal - Rechaza un tipo que no sea TRANSFER")
        void deberiaExigirElTipoTransfer() {
            UUID id = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();
            TransactionType depositType = type(TransactionTypeEnum.DEPOSIT);
            TransactionStatus pendingStatus = status(TransactionStatusEnum.PENDING);
            LocalDateTime now = LocalDateTime.now();
            assertThatThrownBy(() -> Transaction.transfer(id, userId, senderId, receiverId, BigDecimal.TEN, REFERENCE, depositType, pendingStatus, now))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("HU21 - complete y reject")
    class Transitions {

        @Test
        @DisplayName("HU21 CA01 - complete - una PENDING pasa a COMPLETED")
        void deberiaCompletarUnaPendiente() {
            Transaction transaction = pending();

            transaction.complete(status(TransactionStatusEnum.COMPLETED));

            assertThat(transaction.is(TransactionStatusEnum.COMPLETED)).isTrue();
        }

        @Test
        @DisplayName("HU21 CA03 / HU21 CA04 - reject - una PENDING pasa a REJECTED")
        void deberiaRechazarUnaPendiente() {
            Transaction transaction = pending();

            transaction.reject(status(TransactionStatusEnum.REJECTED));

            assertThat(transaction.is(TransactionStatusEnum.REJECTED)).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = TransactionStatusEnum.class, names = {"COMPLETED", "REJECTED", "REVERSED"})
        @DisplayName("HU21 CA03 - complete y reject - solo parten de PENDING: cualquier otro estado se rechaza")
        void noDeberiaCambiarUnaTransaccionQueNoEstaPendiente(TransactionStatusEnum current) {
            Transaction transaction = transfer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "25.00", current);

            TransactionStatus completedStatus = status(TransactionStatusEnum.COMPLETED);
            assertThatThrownBy(() -> transaction.complete(completedStatus))
                    .isInstanceOf(TransactionNotPendingException.class);
            TransactionStatus rejectedStatus = status(TransactionStatusEnum.REJECTED);
            assertThatThrownBy(() -> transaction.reject(rejectedStatus))
                    .isInstanceOf(TransactionNotPendingException.class);
            assertThat(transaction.is(current)).isTrue();
        }

        @Test
        @DisplayName("Transversal - Exige el estado del catálogo que corresponde al cambio pedido")
        void deberiaExigirElEstadoDelCambio() {
            Transaction transaction = pending();

            TransactionStatus rejectedStatus = status(TransactionStatusEnum.REJECTED);
            assertThatThrownBy(() -> transaction.complete(rejectedStatus))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> transaction.reject(null)).isInstanceOf(IllegalArgumentException.class);
            assertThat(transaction.is(TransactionStatusEnum.PENDING)).isTrue();
        }
    }
}
