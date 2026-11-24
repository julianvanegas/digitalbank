package com.udea.digitalbank.transactions.repository;

import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.customer.domain.Customer;
import com.udea.digitalbank.support.integration.RepositoryTest;
import com.udea.digitalbank.transactions.domain.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static com.udea.digitalbank.support.integration.TestData.ACTIVE;
import static com.udea.digitalbank.support.integration.TestData.ADMIN;
import static com.udea.digitalbank.support.integration.TestData.COMPLETED;
import static com.udea.digitalbank.support.integration.TestData.PENDING;
import static com.udea.digitalbank.support.integration.TestData.REJECTED;
import static com.udea.digitalbank.support.integration.TestData.T0;
import static com.udea.digitalbank.support.integration.TestData.TRANSFER;
import static com.udea.digitalbank.support.integration.TestData.WITHDRAWAL;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TransactionRepository contra PostgreSQL")
class TransactionRepositoryIT extends RepositoryTest {

    /** El día de la prueba, como intervalo [desde, hasta). */
    private static final LocalDateTime DAY_START = T0.toLocalDate().atStartOfDay();
    private static final LocalDateTime NEXT_DAY = DAY_START.plusDays(1);

    @Autowired
    private TransactionRepository repository;

    private Customer owner;
    private Account a;
    private Account b;
    private User admin;

    @BeforeEach
    void accounts() {
        owner = data.customer("ana@example.com");
        a = data.account(owner);
        b = data.account(data.customer("beto@example.com"));
        admin = data.user("admin@example.com", ADMIN, ACTIVE, T0, null, T0);
    }

    @Nested
    @DisplayName("Consumo del día (sumCompletedFromAccount y countCompletedFromAccount)")
    class DailyUsage {

        @Test
        @DisplayName("Cuenta solo los movimientos completados del tipo, de la cuenta y dentro de [desde, hasta)")
        void countsOnlyMatchingMovements() {
            data.withdrawal(owner, a, "100.00", COMPLETED, T0);
            data.withdrawal(owner, a, "50.00", COMPLETED, DAY_START);           // borde inferior: entra
            data.withdrawal(owner, a, "30.00", COMPLETED, NEXT_DAY);            // borde superior: no entra
            data.withdrawal(owner, a, "20.00", COMPLETED, DAY_START.minusNanos(1000));
            data.withdrawal(owner, a, "999.00", PENDING, T0);
            data.withdrawal(owner, a, "888.00", REJECTED, T0);
            data.transfer(owner, a, b, "70.00", COMPLETED, T0);                 // otro tipo
            data.withdrawal(owner, b, "40.00", COMPLETED, T0);                  // otra cuenta

            var withdrawal = data.transactionType(WITHDRAWAL);
            var completed = data.transactionStatus(COMPLETED);

            assertThat(repository.sumCompletedFromAccount(a.getId(), withdrawal, completed, DAY_START, NEXT_DAY))
                    .isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(repository.countCompletedFromAccount(a.getId(), withdrawal, completed, DAY_START, NEXT_DAY))
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("Sin movimientos, la suma es cero y no nula")
        void sumIsZeroWithoutMovements() {
            assertThat(repository.sumCompletedFromAccount(a.getId(), data.transactionType(TRANSFER),
                    data.transactionStatus(COMPLETED), DAY_START, NEXT_DAY)).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Extracto de la cuenta (findCompletedOfAccount)")
    class Statement {

        @Test
        @DisplayName("Incluye entradas y salidas completadas del intervalo, de la más reciente a la más antigua")
        void includesIncomingAndOutgoing() {
            Transaction outgoing = data.transfer(owner, a, b, "10.00", COMPLETED, T0.minusHours(3));
            Transaction incoming = data.transfer(owner, b, a, "20.00", COMPLETED, T0.minusHours(2));
            Transaction deposit = data.deposit(admin, a, "30.00", T0.minusHours(1));
            Transaction withdrawal = data.withdrawal(owner, a, "5.00", COMPLETED, T0);
            data.transfer(owner, a, b, "1.00", PENDING, T0);                    // no movió dinero
            data.withdrawal(owner, b, "2.00", COMPLETED, T0);                   // otra cuenta
            data.deposit(admin, a, "3.00", NEXT_DAY);                           // fuera del intervalo

            Page<Transaction> first = page(0);
            Page<Transaction> second = page(1);

            assertThat(first.getTotalElements()).isEqualTo(4);
            assertThat(first.getContent()).extracting(Transaction::getId)
                    .containsExactly(withdrawal.getId(), deposit.getId());
            assertThat(second.getContent()).extracting(Transaction::getId)
                    .containsExactly(incoming.getId(), outgoing.getId());
        }

        @Test
        @DisplayName("Si dos movimientos comparten instante, el id desempata y el orden es estable")
        void tieBreaksById() {
            Transaction x = data.deposit(admin, a, "1.00", T0);
            Transaction y = data.deposit(admin, a, "2.00", T0);
            Transaction z = data.deposit(admin, a, "3.00", T0);
            List<java.util.UUID> expected = Stream.of(x, y, z).map(Transaction::getId)
                    .sorted(Comparator.comparing(java.util.UUID::toString).reversed()).toList();

            assertThat(repository.findCompletedOfAccount(a.getId(), data.transactionStatus(COMPLETED), DAY_START,
                    NEXT_DAY, PageRequest.of(0, 10)).getContent())
                    .extracting(Transaction::getId).containsExactlyElementsOf(expected);
        }

        private Page<Transaction> page(int number) {
            return repository.findCompletedOfAccount(a.getId(), data.transactionStatus(COMPLETED), DAY_START,
                    NEXT_DAY, PageRequest.of(number, 2));
        }
    }

    @Test
    @DisplayName("Busca por comprobante, con y sin bloqueo")
    void findByReference() {
        Transaction transfer = data.transfer(owner, a, b, "10.00", PENDING, T0);

        assertThat(repository.findByReference(transfer.getReference())).isPresent();
        assertThat(repository.findByReferenceForUpdate(transfer.getReference())).get()
                .extracting(Transaction::getId).isEqualTo(transfer.getId());
        assertThat(repository.findByReference("TRF-NO-EXISTE")).isEmpty();
    }

    @Test
    @DisplayName("findPendingFromAccountsForUpdate devuelve solo las pendientes que salen de esas cuentas")
    void pendingFromAccounts() {
        Transaction pendingFromA = data.transfer(owner, a, b, "10.00", PENDING, T0);
        Transaction pendingWithdrawalFromA = data.withdrawal(owner, a, "5.00", PENDING, T0);
        data.transfer(owner, a, b, "20.00", COMPLETED, T0);
        data.transfer(owner, b, a, "30.00", PENDING, T0);                       // sale de otra cuenta

        assertThat(repository.findPendingFromAccountsForUpdate(List.of(a.getId()), data.transactionStatus(PENDING)))
                .extracting(Transaction::getId)
                .containsExactlyInAnyOrder(pendingFromA.getId(), pendingWithdrawalFromA.getId());
    }

    @Test
    @DisplayName("rejectPendingCreatedBefore rechaza las pendientes vencidas y no toca las demás")
    void rejectsExpiredPending() {
        LocalDateTime cutoff = T0;
        Transaction expired = data.transfer(owner, a, b, "10.00", PENDING, cutoff.minusSeconds(1));
        Transaction recent = data.transfer(owner, a, b, "20.00", PENDING, cutoff);
        Transaction completed = data.transfer(owner, a, b, "30.00", COMPLETED, cutoff.minusHours(1));

        int rejected = repository.rejectPendingCreatedBefore(cutoff, data.transactionStatus(PENDING),
                data.transactionStatus(REJECTED));
        em.clear();

        assertThat(rejected).isEqualTo(1);
        assertThat(statusOf(expired)).isEqualTo("REJECTED");
        assertThat(statusOf(recent)).isEqualTo("PENDING");
        assertThat(statusOf(completed)).isEqualTo("COMPLETED");
    }

    private String statusOf(Transaction transaction) {
        return repository.findById(transaction.getId()).orElseThrow().getStatus().getCode();
    }
}
