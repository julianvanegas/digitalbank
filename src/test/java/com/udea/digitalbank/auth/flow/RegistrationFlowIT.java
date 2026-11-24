package com.udea.digitalbank.auth.flow;

import com.udea.digitalbank.support.integration.BankApi;
import com.udea.digitalbank.support.integration.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alta de un cliente contra la base real: el correo de confirmación sale solo cuando la transacción del registro se
 * confirma (listener AFTER_COMMIT), y el usuario pasa por PENDING_VERIFICATION y PENDING_REVIEW antes de poder entrar.
 */
@DisplayName("Registro de cliente y confirmación de correo")
class RegistrationFlowIT extends IntegrationTest {

    private String admin;

    @BeforeEach
    void adminUser() {
        admin = api.admin("admin@example.com");
    }

    @Test
    @DisplayName("El cliente recorre registro, confirmación de correo, revisión y activación")
    void fullRegistration() {
        api.register("ana@example.com", "1020304050").expect(201);
        assertThat(statusOf("ana@example.com")).isEqualTo("PENDING_VERIFICATION");
        api.startLogin("ana@example.com", BankApi.PASSWORD).expect(401);

        api.verifyEmail("ana@example.com", api.lastCode("ana@example.com")).expect(204);
        assertThat(statusOf("ana@example.com")).isEqualTo("PENDING_REVIEW");
        api.startLogin("ana@example.com", BankApi.PASSWORD).expect(403);

        api.changeStatus(admin, "1020304050", "ACTIVE").expect(204);

        assertThat(api.login("ana@example.com", BankApi.PASSWORD)).isNotBlank();
    }

    @Test
    @DisplayName("Un registro rechazado no deja usuario ni envía correo")
    void rejectedRegistrationLeavesNothing() {
        api.register("ana@example.com", "1020304050").expect(201);

        api.register("beto@example.com", "1020304050").expect(409);

        assertThat(jdbc.queryForObject("select count(*) from users where email = 'beto@example.com'", Long.class))
                .isZero();
        assertThat(api.emailsTo("beto@example.com")).isEmpty();
    }

    @Test
    @DisplayName("El código de confirmación no sirve dos veces")
    void confirmationCodeIsSingleUse() {
        api.register("ana@example.com", "1020304050").expect(201);
        String code = api.lastCode("ana@example.com");
        api.verifyEmail("ana@example.com", code).expect(204);

        api.verifyEmail("ana@example.com", code).expect(400);
    }

    private String statusOf(String email) {
        return jdbc.queryForObject(
                "select s.code from users u join user_status s on s.id = u.status_id where u.email = ?",
                String.class, email);
    }
}
