package com.udea.digitalbank.auth.flow;

import com.udea.digitalbank.support.integration.BankApi;
import com.udea.digitalbank.support.integration.BankApi.Customer;
import com.udea.digitalbank.support.integration.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Recuperación de contraseña")
class PasswordResetFlowIT extends IntegrationTest {

    private static final String NEW_PASSWORD = "Nueva#2026ok";

    private Customer ana;

    @BeforeEach
    void customer() {
        ana = api.activeCustomer("ana@example.com", api.admin("admin@example.com"));
    }

    @Test
    @DisplayName("Con el código del correo cambia la contraseña y cierra la sesión abierta")
    void resetsPasswordAndRevokesSession() {
        api.post("/api/v1/auth/recover-password", null, "{\"email\": \"ana@example.com\"}").expect(204);

        api.post("/api/v1/auth/reset-password", null, """
                {"email": "ana@example.com", "code": "%s", "password": "%s"}"""
                .formatted(api.lastCode(ana.email()), NEW_PASSWORD)).expect(204);

        api.get("/api/v1/customers/profile", ana.token()).expect(401);
        api.startLogin(ana.email(), BankApi.PASSWORD).expect(401);
        assertThat(api.login(ana.email(), NEW_PASSWORD)).isNotBlank();
    }

    @Test
    @DisplayName("Con un código equivocado la contraseña no cambia")
    void wrongCodeKeepsPassword() {
        api.post("/api/v1/auth/recover-password", null, "{\"email\": \"ana@example.com\"}").expect(204);

        api.post("/api/v1/auth/reset-password", null, """
                {"email": "ana@example.com", "code": "%s", "password": "%s"}"""
                .formatted(BankApi.wrong(api.lastCode(ana.email())), NEW_PASSWORD)).expect(400);

        api.startLogin(ana.email(), BankApi.PASSWORD).expect(200);
    }

    @Test
    @DisplayName("Para un correo que no existe responde igual y no envía nada")
    void unknownEmailRevealsNothing() {
        api.post("/api/v1/auth/recover-password", null, "{\"email\": \"nadie@example.com\"}").expect(204);

        assertThat(api.emailsTo("nadie@example.com")).isEmpty();
    }
}
