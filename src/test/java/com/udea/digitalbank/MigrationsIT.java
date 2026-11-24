package com.udea.digitalbank;

import com.udea.digitalbank.support.integration.RepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las migraciones de Flyway se aplican completas sobre PostgreSQL y siembran los catálogos con los ids fijos de los
 * que dependen los CHECK y los enums. Que el contexto JPA arranque ya prueba que {@code ddl-auto=validate} acepta el
 * esquema resultante.
 */
@DisplayName("Migraciones de Flyway")
class MigrationsIT extends RepositoryTest {

    @Test
    @DisplayName("Aplica todas las migraciones con éxito y en orden")
    void appliesAllMigrations() {
        List<Object> versions = nativeList(
                "select version from flyway_schema_history where success order by installed_rank");

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6");
    }

    @Test
    @DisplayName("Siembra los catálogos con sus ids fijos")
    void seedsCatalogs() {
        assertThat(codes("roles")).containsExactly("CUSTOMER", "ADMIN");
        assertThat(codes("user_status"))
                .containsExactly("ACTIVE", "PENDING_VERIFICATION", "INACTIVE", "BLOCKED", "PENDING_REVIEW");
        assertThat(nativeList("select purpose from challenge_purposes order by id"))
                .containsExactly("LOGIN", "EMAIL_CONFIRMATION", "PASSWORD_RESET", "TRANSACTION");
        assertThat(codes("account_status")).containsExactly("ACTIVE", "INACTIVE", "CLOSED", "BLOCKED");
        assertThat(codes("account_types")).containsExactly("SAVINGS", "CHECKING");
        assertThat(codes("transaction_types")).containsExactly("TRANSFER", "DEPOSIT", "WITHDRAWAL");
        assertThat(codes("status_transaction")).containsExactly("PENDING", "COMPLETED", "REJECTED", "REVERSED");
        assertThat(nativeList("select count(*) from transaction_limits")).containsExactly(4L);
    }

    @Test
    @DisplayName("La secuencia de números de cuenta existe y no da la vuelta al agotarse")
    void accountNumberSequenceDoesNotCycle() {
        assertThat(nativeList("select cycle from pg_sequences where sequencename = 'account_number_seq'"))
                .containsExactly(false);
    }

    private List<Object> codes(String table) {
        return nativeList("select code from " + table + " order by id");
    }

    @SuppressWarnings("unchecked")
    private List<Object> nativeList(String sql) {
        return (List<Object>) em.getEntityManager().createNativeQuery(sql).getResultList();
    }
}
