package com.udea.digitalbank.transactions.flow;

import com.udea.digitalbank.support.integration.BankApi;
import com.udea.digitalbank.support.integration.BankApi.Customer;
import com.udea.digitalbank.support.integration.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Retiro en dos pasos (ADR 0006) contra la base real. */
@DisplayName("Retiro en dos pasos")
class WithdrawalFlowIT extends IntegrationTest {

    private String admin;
    private Customer ana;
    private String account;

    @BeforeEach
    void account() {
        admin = api.admin("admin@example.com");
        ana = api.activeCustomer("ana@example.com", admin);
        account = api.openAccount(ana.token());
        api.deposit(admin, account, "1000.00").expect(201);
    }

    @Test
    @DisplayName("Con el código correcto descuenta el saldo y completa el retiro")
    void completesWithdrawal() {
        String reference = api.withdraw(ana.token(), account, "300.00").expect(201).json("$.reference");
        assertThat(api.balance(ana.token(), account)).isEqualByComparingTo("1000.00");

        api.confirm(ana.token(), reference, api.lastCode(ana.email())).expect(200);

        assertThat(api.balance(ana.token(), account)).isEqualByComparingTo("700.00");
        assertThat(api.transaction(ana.token(), reference).expect(200).<String>json("$.status"))
                .isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Sin saldo suficiente no se inicia")
    void insufficientFunds() {
        api.withdraw(ana.token(), account, "1000.01").expect(409);

        assertThat(api.balance(ana.token(), account)).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Un código equivocado no descuenta nada")
    void wrongCode() {
        String reference = api.withdraw(ana.token(), account, "300.00").expect(201).json("$.reference");

        api.confirm(ana.token(), reference, BankApi.wrong(api.lastCode(ana.email()))).expect(400);

        assertThat(api.balance(ana.token(), account)).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Si un administrador retira por el cliente, el código llega al cliente con un aviso")
    void adminWithdrawalSendsCodeToOwner() {
        String reference = api.withdraw(admin, account, "100.00").expect(201).json("$.reference");

        assertThat(api.emailsTo(ana.email()).getLast().text())
                .contains("Un empleado del banco inició esta operación a tu nombre");
        api.confirm(admin, reference, api.lastCode(ana.email())).expect(200);
        assertThat(api.balance(ana.token(), account)).isEqualByComparingTo("900.00");
    }
}
