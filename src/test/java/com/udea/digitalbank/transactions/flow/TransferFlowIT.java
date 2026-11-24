package com.udea.digitalbank.transactions.flow;

import com.udea.digitalbank.support.integration.BankApi;
import com.udea.digitalbank.support.integration.BankApi.Customer;
import com.udea.digitalbank.support.integration.BankApi.Response;
import com.udea.digitalbank.support.integration.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU20 / HU21: transferencia en dos pasos contra la base real. Lo que importa aquí es el dinero: los saldos solo
 * cambian al confirmar con el código correcto, y cualquier rechazo deja los dos saldos como estaban.
 */
@DisplayName("HU20 / HU21 - Transferencia en dos pasos")
class TransferFlowIT extends IntegrationTest {

    private String admin;
    private Customer ana;
    private Customer beto;
    private String anaAccount;
    private String betoAccount;

    @BeforeEach
    void accounts() {
        admin = api.admin("admin@example.com");
        ana = api.activeCustomer("ana@example.com", admin);
        beto = api.activeCustomer("beto@example.com", admin);
        anaAccount = api.openAccount(ana.token());
        betoAccount = api.openAccount(beto.token());
        api.deposit(admin, anaAccount, "1000.00").expect(201);
    }

    @Test
    @DisplayName("Con el código correcto mueve el dinero, completa la transacción y envía el comprobante")
    void completesTransfer() {
        String reference = initiate("250.00");
        assertThat(statusOf(reference)).isEqualTo("PENDING");
        assertBalances("1000.00", "0.00");

        Response confirmed = api.confirm(ana.token(), reference, api.lastCode(ana.email())).expect(200);

        assertThat(confirmed.<String>json("$.status")).isEqualTo("COMPLETED");
        assertThat(statusOf(reference)).isEqualTo("COMPLETED");
        assertBalances("750.00", "250.00");
        assertThat(api.emailsTo(ana.email()))
                .anySatisfy(email -> {
                    assertThat(email.subject()).isEqualTo("Comprobante de tu transferencia");
                    assertThat(email.text()).contains(reference);
                });
    }

    @Test
    @DisplayName("Confirmar dos veces devuelve el comprobante y no mueve el dinero otra vez")
    void confirmingTwiceDoesNotMoveMoneyTwice() {
        String reference = initiate("100.00");
        String code = api.lastCode(ana.email());
        api.confirm(ana.token(), reference, code).expect(200);

        api.confirm(ana.token(), reference, code).expect(200);

        assertBalances("900.00", "100.00");
    }

    @Test
    @DisplayName("Sin fondos suficientes no se crea la transacción")
    void insufficientFunds() {
        api.transfer(ana.token(), anaAccount, betoAccount, "1000.01").expect(409);

        assertThat(jdbc.queryForObject("select count(*) from transactions where transaction_type_id = 1",
                Long.class)).isZero();
        assertBalances("1000.00", "0.00");
    }

    @Nested
    @DisplayName("Código de confirmación")
    class Code {

        @Test
        @DisplayName("Un código equivocado no mueve dinero y deja la transferencia pendiente")
        void wrongCodeKeepsPending() {
            String reference = initiate("100.00");
            String code = api.lastCode(ana.email());

            Response wrong = api.confirm(ana.token(), reference, BankApi.wrong(code)).expect(400);

            assertThat(wrong.message()).contains("Te quedan 2 intentos");
            assertThat(statusOf(reference)).isEqualTo("PENDING");
            assertBalances("1000.00", "0.00");
            api.confirm(ana.token(), reference, code).expect(200);
            assertBalances("900.00", "100.00");
        }

        @Test
        @DisplayName("Al tercer código equivocado la transferencia se cancela y ni el correcto la completa")
        void cancelledAfterThreeWrongCodes() {
            String reference = initiate("100.00");
            String code = api.lastCode(ana.email());
            api.confirm(ana.token(), reference, BankApi.wrong(code)).expect(400);
            api.confirm(ana.token(), reference, BankApi.wrong(code)).expect(400);

            api.confirm(ana.token(), reference, BankApi.wrong(code)).expect(429);

            assertThat(statusOf(reference)).isEqualTo("REJECTED");
            api.confirm(ana.token(), reference, code).expect(409);
            assertBalances("1000.00", "0.00");
        }

        @Test
        @DisplayName("Un código vencido (3 minutos) cancela la transferencia")
        void expiredCodeRejects() {
            String reference = initiate("100.00");
            String code = api.lastCode(ana.email());
            CLOCK.advance(Duration.ofMinutes(3).plusSeconds(1));

            api.confirm(ana.token(), reference, code).expect(409);

            assertThat(statusOf(reference)).isEqualTo("REJECTED");
            assertBalances("1000.00", "0.00");
        }
    }

    @Test
    @DisplayName("Iniciar otra transferencia cancela la pendiente anterior")
    void newTransferCancelsPrevious() {
        String first = initiate("100.00");
        String second = initiate("200.00");

        assertThat(statusOf(first)).isEqualTo("REJECTED");
        api.confirm(ana.token(), first, api.lastCode(ana.email())).expect(409);
        api.confirm(ana.token(), second, api.lastCode(ana.email())).expect(200);
        assertBalances("800.00", "200.00");
    }

    @Test
    @DisplayName("Si la cuenta de destino se bloquea antes de confirmar, se rechaza sin mover dinero")
    void receiverBlockedBeforeConfirmation() {
        String reference = initiate("100.00");
        api.patch("/api/v1/accounts/" + betoAccount + "/block", admin, "{\"reason\": \"Bloqueo\"}").expect(200);

        api.confirm(ana.token(), reference, api.lastCode(ana.email())).expect(403);

        assertThat(statusOf(reference)).isEqualTo("REJECTED");
        assertThat(api.balance(admin, anaAccount)).isEqualByComparingTo("1000.00");
        assertThat(api.balance(admin, betoAccount)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Otro cliente no puede confirmar ni consultar la transferencia ajena")
    void otherCustomerCannotConfirm() {
        String reference = initiate("100.00");

        api.confirm(beto.token(), reference, api.lastCode(ana.email())).expect(404);
        api.transaction(beto.token(), reference).expect(404);

        assertThat(statusOf(reference)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Un cliente no puede transferir desde una cuenta ajena: la ve como inexistente")
    void cannotTransferFromOthersAccount() {
        // 404 y no 403: no se revela que la cuenta existe y es de otro cliente
        api.transfer(beto.token(), anaAccount, betoAccount, "10.00").expect(404);

        assertBalances("1000.00", "0.00");
    }

    private String initiate(String amount) {
        return api.transfer(ana.token(), anaAccount, betoAccount, amount).expect(201).json("$.reference");
    }

    private String statusOf(String reference) {
        return jdbc.queryForObject("""
                select s.code from transactions t join status_transaction s on s.id = t.status_id
                where t.reference = ?""", String.class, reference);
    }

    private void assertBalances(String anaBalance, String betoBalance) {
        assertThat(api.balance(ana.token(), anaAccount)).as("saldo de Ana").isEqualByComparingTo(anaBalance);
        assertThat(api.balance(beto.token(), betoAccount)).as("saldo de Beto").isEqualByComparingTo(betoBalance);
    }
}
