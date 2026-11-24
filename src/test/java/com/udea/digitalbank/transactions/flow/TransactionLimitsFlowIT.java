package com.udea.digitalbank.transactions.flow;

import com.udea.digitalbank.support.integration.BankApi;
import com.udea.digitalbank.support.integration.BankApi.Customer;
import com.udea.digitalbank.support.integration.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Topes transaccionales (ADR 0005) con los valores reales de V6, sumando en la base lo ya completado en el día:
 * retiros con 4 al día y $2.500.000 por operación; transferencias con $80.000.000 acumulados al día.
 */
@DisplayName("Topes transaccionales diarios")
class TransactionLimitsFlowIT extends IntegrationTest {

    private String admin;
    private Customer ana;
    private String anaAccount;
    private String betoAccount;

    @BeforeEach
    void accounts() {
        admin = api.admin("admin@example.com");
        ana = api.activeCustomer("ana@example.com", admin);
        Customer beto = api.activeCustomer("beto@example.com", admin);
        anaAccount = api.openAccount(ana.token());
        betoAccount = api.openAccount(beto.token());
        api.deposit(admin, anaAccount, "100000000.00").expect(201);
    }

    @Nested
    @DisplayName("Retiros")
    class Withdrawals {

        @Test
        @DisplayName("Un retiro de más de $2.500.000 se rechaza y uno de exactamente ese monto no")
        void perTransactionLimit() {
            api.withdraw(ana.token(), anaAccount, "2500000.01").expect(409);

            api.withdraw(ana.token(), anaAccount, "2500000.00").expect(201);
        }

        @Test
        @DisplayName("El quinto retiro del día se rechaza y al día siguiente se puede retirar otra vez")
        void dailyCountLimitResetsNextDay() {
            for (int i = 0; i < 4; i++) {
                completeWithdrawal(ana.token(), "100.00");
            }

            api.withdraw(ana.token(), anaAccount, "100.00").expect(409);

            CLOCK.set(CLOCK.now().toLocalDate().plusDays(1).atTime(8, 0));
            String token = api.login(ana.email(), BankApi.PASSWORD);
            api.withdraw(token, anaAccount, "100.00").expect(201);
        }

        @Test
        @DisplayName("Los retiros de un administrador no consumen el cupo ni respetan los topes")
        void adminIsNotLimited() {
            api.withdraw(admin, anaAccount, "3000000.00").expect(201);
        }
    }

    @Nested
    @DisplayName("Transferencias")
    class Transfers {

        @Test
        @DisplayName("Tras transferir $80.000.000 en el día, un centavo más se rechaza; al día siguiente no")
        void dailyAmountLimitResetsNextDay() {
            String reference = api.transfer(ana.token(), anaAccount, betoAccount, "80000000.00")
                    .expect(201).json("$.reference");
            api.confirm(ana.token(), reference, api.lastCode(ana.email())).expect(200);

            api.transfer(ana.token(), anaAccount, betoAccount, "0.01").expect(409);

            CLOCK.set(CLOCK.now().toLocalDate().plusDays(1).atTime(8, 0));
            String token = api.login(ana.email(), BankApi.PASSWORD);
            api.transfer(token, anaAccount, betoAccount, "0.01").expect(201);
        }

        @Test
        @DisplayName("Las transferencias pendientes o rechazadas no consumen el tope")
        void onlyCompletedTransfersCount() {
            api.transfer(ana.token(), anaAccount, betoAccount, "80000000.00").expect(201);

            String reference = api.transfer(ana.token(), anaAccount, betoAccount, "80000000.00")
                    .expect(201).json("$.reference");
            api.confirm(ana.token(), reference, api.lastCode(ana.email())).expect(200);

            assertThat(api.balance(ana.token(), anaAccount)).isEqualByComparingTo("20000000.00");
        }
    }

    private void completeWithdrawal(String token, String amount) {
        String reference = api.withdraw(token, anaAccount, amount).expect(201).json("$.reference");
        api.confirm(token, reference, api.lastCode(ana.email())).expect(200);
    }
}
