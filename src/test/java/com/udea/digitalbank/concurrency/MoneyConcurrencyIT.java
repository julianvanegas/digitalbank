package com.udea.digitalbank.concurrency;

import com.udea.digitalbank.support.integration.BankApi.Customer;
import com.udea.digitalbank.support.integration.BankApi.Response;
import com.udea.digitalbank.support.integration.Concurrently;
import com.udea.digitalbank.support.integration.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Carreras reales sobre el dinero: varias peticiones llegan a la vez y los bloqueos de fila (SELECT ... FOR UPDATE)
 * deben serializarlas. Sin ellos, estas pruebas pierden actualizaciones de saldo, mueven el dinero dos veces o
 * terminan en un interbloqueo.
 */
@DisplayName("Concurrencia: saldos y confirmaciones simultáneas")
class MoneyConcurrencyIT extends IntegrationTest {

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
        api.deposit(admin, betoAccount, "1000.00").expect(201);
    }

    @Test
    @DisplayName("Cinco confirmaciones simultáneas de la misma transferencia mueven el dinero una sola vez")
    void simultaneousConfirmationsMoveMoneyOnce() {
        String reference = api.transfer(ana.token(), anaAccount, betoAccount, "100.00")
                .expect(201).json("$.reference");
        String code = api.lastCode(ana.email());

        List<Response> responses = Concurrently.times(5, () -> api.confirm(ana.token(), reference, code));

        assertThat(responses).as("respuestas: %s", responses).extracting(Response::status).containsOnly(200);
        assertThat(api.balance(admin, anaAccount)).isEqualByComparingTo("900.00");
        assertThat(api.balance(admin, betoAccount)).isEqualByComparingTo("1100.00");
        assertThat(api.emailsTo(ana.email()))
                .filteredOn(email -> email.subject().equals("Comprobante de tu transferencia")).hasSize(1);
    }

    @Test
    @DisplayName("Transferencias cruzadas A→B y B→A confirmadas a la vez terminan sin interbloqueo")
    void crossedTransfersDoNotDeadlock() {
        for (int round = 0; round < 5; round++) {
            String toBeto = api.transfer(ana.token(), anaAccount, betoAccount, "10.00")
                    .expect(201).json("$.reference");
            String anaCode = api.lastCode(ana.email());
            String toAna = api.transfer(beto.token(), betoAccount, anaAccount, "4.00")
                    .expect(201).json("$.reference");
            String betoCode = api.lastCode(beto.email());

            List<Response> responses = Concurrently.run(List.<Callable<Response>>of(
                    () -> api.confirm(ana.token(), toBeto, anaCode),
                    () -> api.confirm(beto.token(), toAna, betoCode)));

            assertThat(responses).as("ronda %d: %s", round, responses).extracting(Response::status).containsOnly(200);
        }

        BigDecimal anaBalance = api.balance(admin, anaAccount);
        BigDecimal betoBalance = api.balance(admin, betoAccount);
        assertThat(anaBalance).isEqualByComparingTo("970.00");
        assertThat(betoBalance).isEqualByComparingTo("1030.00");
        assertThat(anaBalance.add(betoBalance)).as("el dinero total se conserva").isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("Diez depósitos simultáneos en la misma cuenta suman todos")
    void simultaneousDepositsAreNotLost() {
        List<Response> responses = Concurrently.times(10, () -> api.deposit(admin, anaAccount, "10.00"));

        // Primero lo que nunca puede fallar: el saldo refleja exactamente los depósitos que se aceptaron
        long accepted = responses.stream().filter(r -> r.status() == 201).count();
        assertThat(api.balance(admin, anaAccount)).as("saldo frente a %d depósitos aceptados", accepted)
                .isEqualByComparingTo(new BigDecimal("1000.00").add(BigDecimal.TEN.multiply(BigDecimal.valueOf(accepted))));
        assertThat(jdbc.queryForObject("select count(*) from transactions where transaction_type_id = 2", Long.class))
                .isEqualTo(2 + accepted);
        assertThat(responses).as("respuestas: %s", responses).extracting(Response::status).containsOnly(201);
        assertThat(api.balance(admin, anaAccount)).isEqualByComparingTo("1100.00");
    }

    @Test
    @DisplayName("Una confirmación y varios depósitos simultáneos sobre la misma cuenta no pierden saldo")
    void confirmationAndDepositsOnSameAccount() {
        String reference = api.transfer(ana.token(), anaAccount, betoAccount, "300.00")
                .expect(201).json("$.reference");
        String code = api.lastCode(ana.email());
        List<Callable<Response>> tasks = new ArrayList<>();
        tasks.add(() -> api.confirm(ana.token(), reference, code));
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> api.deposit(admin, anaAccount, "20.00"));
        }

        List<Response> responses = Concurrently.run(tasks);

        assertThat(responses.getFirst().status()).isEqualTo(200);
        assertThat(responses.subList(1, responses.size())).extracting(Response::status).containsOnly(201);
        assertThat(api.balance(admin, anaAccount)).isEqualByComparingTo("800.00");
        assertThat(api.balance(admin, betoAccount)).isEqualByComparingTo("1300.00");
    }
}
