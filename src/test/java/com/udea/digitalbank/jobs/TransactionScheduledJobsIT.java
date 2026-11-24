package com.udea.digitalbank.jobs;

import com.udea.digitalbank.support.integration.BankApi;
import com.udea.digitalbank.support.integration.BankApi.Customer;
import com.udea.digitalbank.support.integration.IntegrationTest;
import com.udea.digitalbank.transactions.service.TransactionScheduledJobs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Rechazo periódico de transferencias y retiros pendientes cuyo código (3 minutos) ya venció. */
@DisplayName("Trabajo programado de transacciones pendientes vencidas")
class TransactionScheduledJobsIT extends IntegrationTest {

    private static final Duration CODE_TTL = Duration.ofMinutes(3);

    @Autowired
    private TransactionScheduledJobs jobs;

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
    @DisplayName("Rechaza la pendiente solo cuando pasó el plazo del código, no justo en el límite")
    void rejectsOnlyAfterCodeExpires() {
        String reference = transfer(ana, anaAccount, betoAccount, "100.00");

        CLOCK.advance(CODE_TTL);
        jobs.rejectExpiredPending();
        assertThat(statusOf(reference)).isEqualTo("PENDING");

        CLOCK.advance(Duration.ofSeconds(1));
        jobs.rejectExpiredPending();
        assertThat(statusOf(reference)).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("Rechaza transferencias y retiros vencidos sin mover dinero ni tocar los completados")
    void rejectsExpiredWithoutTouchingOthers() {
        String completed = transfer(beto, betoAccount, anaAccount, "50.00");
        api.confirm(beto.token(), completed, api.lastCode(beto.email())).expect(200);
        String pendingTransfer = transfer(ana, anaAccount, betoAccount, "100.00");
        String pendingWithdrawal = api.withdraw(beto.token(), betoAccount, "30.00").expect(201).json("$.reference");
        CLOCK.advance(CODE_TTL.plusSeconds(1));

        jobs.rejectExpiredPending();

        assertThat(statusOf(pendingTransfer)).isEqualTo("REJECTED");
        assertThat(statusOf(pendingWithdrawal)).isEqualTo("REJECTED");
        assertThat(statusOf(completed)).isEqualTo("COMPLETED");
        assertThat(api.balance(admin, anaAccount)).isEqualByComparingTo("1050.00");
        assertThat(api.balance(admin, betoAccount)).isEqualByComparingTo("950.00");
    }

    @Test
    @DisplayName("Una transferencia rechazada por el trabajo ya no se puede confirmar")
    void rejectedTransferCannotBeConfirmed() {
        String reference = transfer(ana, anaAccount, betoAccount, "100.00");
        String code = api.lastCode(ana.email());
        CLOCK.advance(CODE_TTL.plusSeconds(1));
        jobs.rejectExpiredPending();
        String token = api.login(ana.email(), BankApi.PASSWORD);

        api.confirm(token, reference, code).expect(409);

        assertThat(api.balance(admin, anaAccount)).isEqualByComparingTo("1000.00");
    }

    private String transfer(Customer from, String fromAccount, String toAccount, String amount) {
        return api.transfer(from.token(), fromAccount, toAccount, amount).expect(201).json("$.reference");
    }

    private String statusOf(String reference) {
        return jdbc.queryForObject("""
                select s.code from transactions t join status_transaction s on s.id = t.status_id
                where t.reference = ?""", String.class, reference);
    }
}
