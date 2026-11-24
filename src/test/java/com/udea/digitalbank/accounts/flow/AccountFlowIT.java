package com.udea.digitalbank.accounts.flow;

import com.udea.digitalbank.support.integration.BankApi.Customer;
import com.udea.digitalbank.support.integration.BankApi.Response;
import com.udea.digitalbank.support.integration.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Cuentas: apertura, cupo, cierre, bloqueo y depósitos")
class AccountFlowIT extends IntegrationTest {

    private String admin;
    private Customer ana;

    @BeforeEach
    void customer() {
        admin = api.admin("admin@example.com");
        ana = api.activeCustomer("ana@example.com", admin);
    }

    @Nested
    @DisplayName("Apertura y cupo de tres cuentas")
    class Opening {

        @Test
        @DisplayName("La cuenta nace activa, sin saldo, con número de 12 dígitos y llave del titular")
        void opensActiveEmptyAccount() {
            Response account = api.post("/api/v1/accounts", ana.token(), null).expect(201);

            assertThat(account.<String>json("$.accountNumber")).matches("\\d{12}");
            assertThat(account.<String>json("$.accountKey")).matches("@AN\\d{3}[1-9]\\d*");
            assertThat(account.<String>json("$.status")).isEqualTo("ACTIVE");
            assertThat(account.<String>json("$.accountType")).isEqualTo("SAVINGS");
            assertThat(api.balance(ana.token(), account.json("$.accountNumber"))).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("La cuarta cuenta abierta se rechaza y los números no se repiten")
        void rejectsFourthOpenAccount() {
            List<String> numbers = List.of(api.openAccount(ana.token()), api.openAccount(ana.token()),
                    api.openAccount(ana.token()));

            api.post("/api/v1/accounts", ana.token(), null).expect(409);

            assertThat(numbers).doesNotHaveDuplicates();
            assertThat(countAccounts()).isEqualTo(3);
        }

        @Test
        @DisplayName("Cerrar una cuenta libera su cupo")
        void closingFreesQuota() {
            String first = api.openAccount(ana.token());
            api.openAccount(ana.token());
            api.openAccount(ana.token());

            api.patch("/api/v1/accounts/" + first + "/close", ana.token(), null).expect(200);

            api.post("/api/v1/accounts", ana.token(), null).expect(201);
            assertThat(countAccounts()).isEqualTo(4);
        }
    }

    @Test
    @DisplayName("Una cuenta con saldo no se puede cerrar")
    void cannotCloseAccountWithBalance() {
        String account = api.openAccount(ana.token());
        api.deposit(admin, account, "10.00").expect(201);

        api.patch("/api/v1/accounts/" + account + "/close", ana.token(), null).expect(409);

        assertThat(api.get("/api/v1/accounts/" + account, ana.token()).expect(200).<String>json("$.status"))
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("El bloqueo y el desbloqueo quedan en el historial de acciones de la cuenta")
    void blockAndUnblockAreRecorded() {
        String account = api.openAccount(ana.token());

        api.patch("/api/v1/accounts/" + account + "/block", admin, "{\"reason\": \"Sospecha de fraude\"}")
                .expect(200);
        api.patch("/api/v1/accounts/" + account + "/unblock", admin, "{\"reason\": \"Revisado\"}").expect(200);

        assertThat(jdbc.queryForList("""
                select action from account_actions a join accounts c on c.id = a.account_id
                where c.account_number = ? order by a.created_at""", String.class, account))
                .containsExactly("BLOCKED", "UNBLOCKED");
    }

    @Nested
    @DisplayName("Depósitos")
    class Deposits {

        @Test
        @DisplayName("Suman al saldo y aparecen en el historial de la cuenta")
        void depositAddsToBalanceAndHistory() {
            String account = api.openAccount(ana.token());

            api.deposit(admin, account, "150.50").expect(201);
            api.deposit(admin, account, "49.50").expect(201);

            assertThat(api.balance(ana.token(), account)).isEqualByComparingTo(new BigDecimal("200.00"));
            LocalDate today = CLOCK.now().toLocalDate();
            Response history = api.get("/api/v1/transactions?accountNumber=" + account + "&from=" + today
                    + "&to=" + today, ana.token()).expect(200);
            assertThat(history.<Integer>json("$.totalElements")).isEqualTo(2);
        }

        @Test
        @DisplayName("Una cuenta bloqueada no recibe depósitos y su saldo no cambia")
        void blockedAccountRejectsDeposit() {
            String account = api.openAccount(ana.token());
            api.patch("/api/v1/accounts/" + account + "/block", admin, "{\"reason\": \"Bloqueo\"}").expect(200);

            api.deposit(admin, account, "10.00").expect(400);

            assertThat(api.balance(admin, account)).isEqualByComparingTo("0");
            assertThat(jdbc.queryForObject("select count(*) from transactions", Long.class)).isZero();
        }

        @Test
        @DisplayName("Un cliente no puede hacer depósitos")
        void customerCannotDeposit() {
            String account = api.openAccount(ana.token());

            api.deposit(ana.token(), account, "10.00").expect(403);
        }
    }

    private long countAccounts() {
        return jdbc.queryForObject("select count(*) from accounts where customer_id = ?", Long.class, ana.id());
    }
}
