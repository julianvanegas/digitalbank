package com.udea.digitalbank.jobs;

import com.udea.digitalbank.auth.service.AuthScheduledJobs;
import com.udea.digitalbank.support.integration.BankApi;
import com.udea.digitalbank.support.integration.BankApi.Customer;
import com.udea.digitalbank.support.integration.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trabajos programados de auth contra la base real. En el perfil de pruebas no corren solos
 * ({@code scheduling.enabled=false}): cada prueba prepara los datos, adelanta el reloj y los invoca.
 */
@DisplayName("Trabajos programados de auth")
class AuthScheduledJobsIT extends IntegrationTest {

    private static final Period ONE_YEAR = Period.ofMonths(12);

    @Autowired
    private AuthScheduledJobs jobs;

    private String adminEmail;
    private String admin;

    @BeforeEach
    void adminUser() {
        adminEmail = "admin@example.com";
        admin = api.admin(adminEmail);
    }

    @Nested
    @DisplayName("Inactivar clientes sin actividad en 12 meses (markInactive)")
    class MarkInactive {

        @Test
        @DisplayName("Inactiva al cliente sin ingresos en 12 meses y cierra su sesión")
        void inactivatesIdleCustomer() {
            Customer ana = api.activeCustomer("ana@example.com", admin);
            advance(ONE_YEAR.plusDays(1));

            jobs.markInactive();

            assertThat(statusOf(ana.userId())).isEqualTo("INACTIVE");
            assertThat(sessionsOf(ana.userId())).isZero();
            api.startLogin(ana.email(), BankApi.PASSWORD).expect(403);
        }

        @Test
        @DisplayName("No toca al cliente que ingresó hace menos de 12 meses")
        void keepsRecentlyActiveCustomer() {
            Customer ana = api.activeCustomer("ana@example.com", admin);
            advance(Period.ofMonths(11));
            api.login(ana.email(), BankApi.PASSWORD);
            advance(Period.ofMonths(2));

            jobs.markInactive();

            assertThat(statusOf(ana.userId())).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("No toca al cliente que un administrador reactivó hace poco, aunque no haya ingresado")
        void keepsRecentlyReactivatedCustomer() {
            Customer ana = api.activeCustomer("ana@example.com", admin);
            advance(ONE_YEAR.plusDays(1));
            String freshAdmin = api.login(adminEmail, BankApi.PASSWORD);
            api.changeStatus(freshAdmin, ana.documentNumber(), "BLOCKED").expect(204);
            api.changeStatus(freshAdmin, ana.documentNumber(), "ACTIVE").expect(204);

            jobs.markInactive();

            assertThat(statusOf(ana.userId())).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("Nunca inactiva administradores")
        void neverInactivatesAdmins() {
            UUID adminId = jdbc.queryForObject("select id from users where email = ?", UUID.class, adminEmail);
            advance(ONE_YEAR.plusDays(1));

            jobs.markInactive();

            assertThat(statusOf(adminId)).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("Procesa por lotes: inactiva a los 1.201 clientes ociosos, más que un lote de 500")
        void processesMoreThanOneBatch() {
            List<UUID> idle = insertIdleCustomers(1201, CLOCK.now().minusMonths(13));

            jobs.markInactive();

            assertThat(jdbc.queryForObject("""
                    select count(*) from users u join user_status s on s.id = u.status_id
                    where s.code = 'INACTIVE' and u.status_changed_at = ?""", Long.class, CLOCK.now()))
                    .isEqualTo(idle.size());
        }
    }

    @Nested
    @DisplayName("Limpiar códigos y sesiones vencidos (cleanExpired)")
    class CleanExpired {

        @Test
        @DisplayName("Borra la sesión vencida por inactividad y conserva la que se renovó")
        void deletesOnlyExpiredSessions() {
            Customer ana = api.activeCustomer("ana@example.com", admin);
            Customer beto = api.activeCustomer("beto@example.com", admin);
            CLOCK.advance(Duration.ofMinutes(10));
            api.get("/api/v1/customers/profile", beto.token()).expect(200);
            CLOCK.advance(Duration.ofMinutes(6));

            jobs.cleanExpired();

            assertThat(sessionsOf(ana.userId())).isZero();
            assertThat(sessionsOf(beto.userId())).isOne();
        }

        @Test
        @DisplayName("Borra los códigos vencidos y conserva los vigentes")
        void deletesOnlyExpiredCodes() {
            Customer ana = api.activeCustomer("ana@example.com", admin);
            Customer beto = api.activeCustomer("beto@example.com", admin);
            api.startLogin(ana.email(), BankApi.PASSWORD).expect(200);
            CLOCK.advance(Duration.ofMinutes(4));
            api.startLogin(beto.email(), BankApi.PASSWORD).expect(200);
            CLOCK.advance(Duration.ofMinutes(2));

            jobs.cleanExpired();

            assertThat(challengesOf(ana.userId())).isZero();
            assertThat(challengesOf(beto.userId())).isOne();
        }
    }

    private void advance(Period period) {
        CLOCK.set(CLOCK.now().plus(period));
    }

    private List<UUID> insertIdleCustomers(int count, LocalDateTime lastActivity) {
        List<UUID> ids = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            rows.add(new Object[]{id, "idle" + i + "@example.com", lastActivity, lastActivity, lastActivity,
                    lastActivity});
        }
        jdbc.batchUpdate("""
                insert into users (id, email, password_hash, password_changed_at, role_id, status_id,
                                   failed_attempts, last_login_at, status_changed_at, created_at)
                values (?, ?, 'hash-de-prueba', ?, 1, 1, 0, ?, ?, ?)""", rows);
        return ids;
    }

    private String statusOf(UUID userId) {
        return jdbc.queryForObject(
                "select s.code from users u join user_status s on s.id = u.status_id where u.id = ?",
                String.class, userId);
    }

    private long sessionsOf(UUID userId) {
        return jdbc.queryForObject("select count(*) from auth_sessions where user_id = ?", Long.class, userId);
    }

    private long challengesOf(UUID userId) {
        return jdbc.queryForObject("select count(*) from verification_challenges where user_id = ?", Long.class,
                userId);
    }
}
