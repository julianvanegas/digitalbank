package com.udea.digitalbank.accounts.repository;

import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.domain.AccountStatus;
import com.udea.digitalbank.customer.domain.Customer;
import com.udea.digitalbank.support.integration.RepositoryTest;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AccountRepository contra PostgreSQL")
class AccountRepositoryIT extends RepositoryTest {

    private static final short CLOSED = 3;

    @Autowired
    private AccountRepository repository;

    @Test
    @DisplayName("La secuencia entrega un valor distinto y creciente en cada llamada")
    void sequenceGivesDistinctValues() {
        long first = repository.nextAccountNumberValue();
        long second = repository.nextAccountNumberValue();

        assertThat(second).isGreaterThan(first);
    }

    @Test
    @DisplayName("findKeysByPrefix devuelve solo las llaves que empiezan por el prefijo")
    void findKeysByPrefix() {
        Customer customer = data.customer("ana@example.com");
        data.account(customer, "100000000001", "@AN1231", (short) 1);
        data.account(customer, "100000000002", "@AN12310", (short) 2);
        Customer other = data.customer("otro@example.com");
        data.account(other, "100000000003", "@AN1241", (short) 1);

        assertThat(repository.findKeysByPrefix("@AN123")).containsExactlyInAnyOrder("@AN1231", "@AN12310");
    }

    @Test
    @DisplayName("Busca por número de cuenta (columna CHAR(12))")
    void findByAccountNumber() {
        Account account = data.account(data.customer("ana@example.com"));

        assertThat(repository.findByAccountNumber(account.getAccountNumber())).get()
                .extracting(Account::getId).isEqualTo(account.getId());
        assertThat(repository.findByAccountNumberForUpdate(account.getAccountNumber())).isPresent();
        assertThat(repository.findCustomerIdByAccountNumber(account.getAccountNumber()))
                .contains(account.getCustomerId());
        assertThat(repository.findByAccountNumber("999999999999")).isEmpty();
    }

    @Test
    @DisplayName("Lista las cuentas del cliente en orden de apertura")
    void listsCustomerAccountsByCreation() {
        Customer customer = data.customer("ana@example.com");
        Account newest = data.account(customer, "100000000001", "@AN1231", (short) 1);
        Account oldest = data.account(customer, "100000000002", "@AN1232", (short) 2);
        data.account(data.customer("otro@example.com"));
        setCreatedAt(newest, LocalDateTime.of(2026, 3, 1, 0, 0));
        setCreatedAt(oldest, LocalDateTime.of(2026, 1, 1, 0, 0));

        assertThat(repository.findByCustomerIdOrderByCreatedAt(customer.getId()))
                .extracting(Account::getId).containsExactly(oldest.getId(), newest.getId());
    }

    @Test
    @DisplayName("Las consultas con bloqueo devuelven las cuentas ordenadas por id")
    void lockingQueriesOrderById() {
        Customer customer = data.customer("ana@example.com");
        Account a = data.account(customer, "100000000001", "@AN1231", (short) 1);
        Account b = data.account(customer, "100000000002", "@AN1232", (short) 2);
        Account c = data.account(customer, "100000000003", "@AN1233", (short) 3);
        List<UUID> expected = sortedLikePostgres(a.getId(), b.getId(), c.getId());

        assertThat(repository.findAllByIdForUpdate(List.of(c.getId(), a.getId(), b.getId())))
                .extracting(Account::getId).containsExactlyElementsOf(expected);
        assertThat(repository.findByCustomerIdForUpdate(customer.getId()))
                .extracting(Account::getId).containsExactlyElementsOf(expected);
    }

    @Nested
    @DisplayName("Cupo de tres cuentas abiertas (índice único parcial)")
    class OpenAccountQuota {

        @Test
        @DisplayName("La base rechaza dos cuentas abiertas del mismo cliente en la misma posición")
        void rejectsDuplicatedOpenIndex() {
            Customer customer = data.customer("ana@example.com");
            data.account(customer, "100000000001", "@AN1231", (short) 1);

            assertThatThrownBy(() -> data.account(customer, "100000000002", "@AN1232", (short) 1))
                    .isInstanceOf(ConstraintViolationException.class)
                    .extracting("constraintName").isEqualTo("uk_accounts_customer_index_open");
        }

        @Test
        @DisplayName("Cerrar una cuenta libera su posición")
        void closingReleasesIndex() {
            Customer customer = data.customer("ana@example.com");
            Account closed = data.account(customer, "100000000001", "@AN1231", (short) 1);
            closed.close(em.find(AccountStatus.class, CLOSED), LocalDateTime.now().plusMinutes(1));
            em.flush();

            Account reopened = data.account(customer, "100000000002", "@AN1232", (short) 1);

            assertThat(repository.findByCustomerIdOrderByCreatedAt(customer.getId()))
                    .extracting(Account::getId).containsExactlyInAnyOrder(closed.getId(), reopened.getId());
        }
    }

    private void setCreatedAt(Account account, LocalDateTime createdAt) {
        em.getEntityManager().createNativeQuery("update accounts set created_at = ?1 where id = ?2")
                .setParameter(1, createdAt).setParameter(2, account.getId()).executeUpdate();
        em.clear();
    }

    /** PostgreSQL ordena los uuid byte a byte, que equivale al orden de su texto en hexadecimal. */
    static List<UUID> sortedLikePostgres(UUID... ids) {
        return java.util.Arrays.stream(ids).sorted(Comparator.comparing(UUID::toString)).toList();
    }
}
