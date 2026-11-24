package com.udea.digitalbank.concurrency;

import com.udea.digitalbank.support.integration.BankApi;
import com.udea.digitalbank.support.integration.BankApi.Customer;
import com.udea.digitalbank.support.integration.BankApi.Response;
import com.udea.digitalbank.support.integration.Concurrently;
import com.udea.digitalbank.support.integration.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Carreras sobre los códigos de verificación y sobre las cuentas de un mismo cliente: el contador de intentos y el
 * cupo de cuentas tienen que respetarse aunque las peticiones lleguen todas a la vez.
 */
@DisplayName("Concurrencia: intentos de código, cupo de cuentas y cierres")
class CodesAndAccountsConcurrencyIT extends IntegrationTest {

    private String admin;
    private Customer ana;

    @BeforeEach
    void customer() {
        admin = api.admin("admin@example.com");
        ana = api.activeCustomer("ana@example.com", admin);
    }

    @Test
    @DisplayName("Seis códigos de transacción equivocados a la vez: solo cuentan tres intentos y se cancela")
    void simultaneousWrongTransactionCodes() {
        String from = api.openAccount(ana.token());
        String to = api.openAccount(api.activeCustomer("beto@example.com", admin).token());
        api.deposit(admin, from, "1000.00").expect(201);
        String reference = api.transfer(ana.token(), from, to, "100.00").expect(201).json("$.reference");
        String code = api.lastCode(ana.email());

        List<Response> responses = Concurrently.times(6,
                () -> api.confirm(ana.token(), reference, BankApi.wrong(code)));

        // Dos fallos con intentos restantes, el tercero cancela (429) y los demás encuentran la transferencia ya
        // cancelada (409). Si el contador perdiera actualizaciones habría más de dos 400 y ningún 429.
        assertThat(responses).as("respuestas: %s", responses).extracting(Response::status).containsExactlyInAnyOrder(400, 400, 429, 409, 409, 409);
        api.confirm(ana.token(), reference, code).expect(409);
        assertThat(api.balance(admin, from)).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Seis códigos de login equivocados a la vez agotan el código y el correcto ya no sirve")
    void simultaneousWrongLoginCodes() {
        String challengeId = api.startLogin(ana.email(), BankApi.PASSWORD).expect(200).json("$.challengeId");
        String code = api.lastCode(ana.email());

        List<Response> responses = Concurrently.times(6,
                () -> api.verifyTwoFactor(challengeId, BankApi.wrong(code)));

        assertThat(responses).as("respuestas: %s", responses).extracting(Response::status).containsOnly(400);
        api.verifyTwoFactor(challengeId, code).expect(400);
        assertThat(jdbc.queryForObject("select count(*) from verification_challenges where id = ?::uuid",
                Long.class, challengeId)).isZero();
    }

    @Test
    @DisplayName("Seis aperturas simultáneas nunca superan el cupo de tres ni repiten números")
    void simultaneousOpeningsNeverExceedQuota() {
        List<Response> responses = Concurrently.times(6,
                () -> api.post("/api/v1/accounts", ana.token(), null));

        // Por diseño (V4), dos aperturas que eligen la misma posición chocan contra el índice único parcial y la
        // segunda recibe 409 aunque quede cupo: la base garantiza el máximo, no que se aprovechen todas las posiciones
        List<Response> opened = responses.stream().filter(r -> r.status() == 201).toList();
        assertThat(responses).as("respuestas: %s", responses).extracting(Response::status).containsOnly(201, 409);
        assertThat(opened).hasSizeBetween(1, 3)
                .extracting(r -> r.<String>json("$.accountNumber")).doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject("select count(*) from accounts where customer_id = ?", Long.class, ana.id()))
                .isEqualTo(opened.size());
    }

    @Test
    @DisplayName("Cerrar a la vez las dos últimas cuentas deja al cliente inactivo")
    void simultaneousClosingOfLastAccounts() {
        String first = api.openAccount(ana.token());
        String second = api.openAccount(ana.token());

        List<Response> responses = Concurrently.run(List.<Callable<Response>>of(
                () -> api.patch("/api/v1/accounts/" + first + "/close", ana.token(), null),
                () -> api.patch("/api/v1/accounts/" + second + "/close", ana.token(), null)));

        assertThat(responses).as("respuestas: %s", responses).extracting(Response::status).containsOnly(200);
        // Si los dos cierres no se turnaran, cada uno vería abierta la otra cuenta y nadie inactivaría al cliente
        assertThat(jdbc.queryForObject(
                "select s.code from users u join user_status s on s.id = u.status_id where u.id = ?",
                String.class, ana.userId())).isEqualTo("INACTIVE");
    }
}
