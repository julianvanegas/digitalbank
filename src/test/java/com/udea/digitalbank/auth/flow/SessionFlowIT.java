package com.udea.digitalbank.auth.flow;

import com.udea.digitalbank.support.integration.BankApi;
import com.udea.digitalbank.support.integration.BankApi.Customer;
import com.udea.digitalbank.support.integration.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sesión única con JWT contra la base real: el token solo vale mientras su sesión siga en auth_sessions, vigente por
 * inactividad y con el usuario ACTIVE.
 */
@DisplayName("Sesión: login con 2FA, cierre, inactividad y sesión única")
class SessionFlowIT extends IntegrationTest {

    private static final String PROFILE = "/api/v1/customers/profile";
    private static final Duration INACTIVITY = Duration.ofMinutes(15);

    private String admin;
    private Customer ana;

    @BeforeEach
    void customer() {
        admin = api.admin("admin@example.com");
        ana = api.activeCustomer("ana@example.com", admin);
    }

    @Test
    @DisplayName("Con el token del segundo factor el cliente accede a su perfil")
    void tokenGivesAccess() {
        BankApi.Response profile = api.get(PROFILE, ana.token()).expect(200);

        assertThat(profile.<String>json("$.email")).isEqualTo("ana@example.com");
        assertThat(jdbc.queryForObject("select last_login_at is not null from users where id = ?", Boolean.class,
                ana.userId())).isTrue();
    }

    @Test
    @DisplayName("Tras cerrar sesión, el mismo token ya no sirve")
    void logoutRevokesToken() {
        api.post("/api/v1/auth/logout", ana.token(), null).expect(204);

        api.get(PROFILE, ana.token()).expect(401);
    }

    @Test
    @DisplayName("Un login nuevo invalida el token anterior (sesión única)")
    void newLoginInvalidatesPreviousToken() {
        String second = api.login(ana.email(), BankApi.PASSWORD);

        api.get(PROFILE, ana.token()).expect(401);
        api.get(PROFILE, second).expect(200);
    }

    @Nested
    @DisplayName("Inactividad")
    class Inactivity {

        @Test
        @DisplayName("La sesión vence tras el tiempo de inactividad")
        void expiresAfterInactivity() {
            CLOCK.advance(INACTIVITY.plusSeconds(1));

            api.get(PROFILE, ana.token()).expect(401);
        }

        @Test
        @DisplayName("Cada petición renueva el plazo de inactividad")
        void eachRequestRenewsSession() {
            CLOCK.advance(INACTIVITY.minusMinutes(1));
            api.get(PROFILE, ana.token()).expect(200);

            CLOCK.advance(INACTIVITY.minusMinutes(1));

            api.get(PROFILE, ana.token()).expect(200);
        }
    }

    @Test
    @DisplayName("Si un administrador bloquea al cliente, su token deja de servir")
    void blockingCustomerRevokesAccess() {
        api.changeStatus(admin, ana.documentNumber(), "BLOCKED").expect(204);

        api.get(PROFILE, ana.token()).expect(401);
        api.startLogin(ana.email(), BankApi.PASSWORD).expect(403);
    }

    @Nested
    @DisplayName("Segundo factor")
    class TwoFactor {

        @Test
        @DisplayName("Un código equivocado no entrega token y el correcto sí")
        void wrongThenRightCode() {
            String challengeId = api.startLogin(ana.email(), BankApi.PASSWORD).expect(200).json("$.challengeId");
            String code = api.lastCode(ana.email());

            api.verifyTwoFactor(challengeId, BankApi.wrong(code)).expect(400);

            assertThat(api.verifyTwoFactor(challengeId, code).expect(200).<String>json("$.token")).isNotBlank();
        }

        @Test
        @DisplayName("El código vence a los 5 minutos")
        void codeExpires() {
            String challengeId = api.startLogin(ana.email(), BankApi.PASSWORD).expect(200).json("$.challengeId");
            CLOCK.advance(Duration.ofMinutes(5).plusSeconds(1));

            api.verifyTwoFactor(challengeId, api.lastCode(ana.email())).expect(400);
        }

        @Test
        @DisplayName("Al tercer código equivocado se cancela y ni el correcto sirve")
        void codeCancelledAfterThreeFailures() {
            String challengeId = api.startLogin(ana.email(), BankApi.PASSWORD).expect(200).json("$.challengeId");
            String code = api.lastCode(ana.email());

            for (int i = 0; i < 3; i++) {
                api.verifyTwoFactor(challengeId, BankApi.wrong(code)).expect(400);
            }

            api.verifyTwoFactor(challengeId, code).expect(400);
        }
    }
}
