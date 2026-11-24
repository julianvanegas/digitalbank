package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.support.integration.RepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.udea.digitalbank.support.integration.TestData.ACTIVE;
import static com.udea.digitalbank.support.integration.TestData.ADMIN;
import static com.udea.digitalbank.support.integration.TestData.CUSTOMER;
import static com.udea.digitalbank.support.integration.TestData.INACTIVE;
import static com.udea.digitalbank.support.integration.TestData.T0;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserRepository contra PostgreSQL")
class UserRepositoryIT extends RepositoryTest {

    @Autowired
    private UserRepository repository;

    @Nested
    @DisplayName("Búsqueda por correo")
    class ByEmail {

        @Test
        @DisplayName("Encuentra al usuario aunque el correo llegue con mayúsculas y espacios")
        void normalizesEmail() {
            User saved = data.customerUser("ana@example.com");

            assertThat(repository.findByEmail("  Ana@Example.COM ")).get()
                    .extracting(User::getId).isEqualTo(saved.getId());
            assertThat(repository.existsByEmail(" ANA@EXAMPLE.COM")).isTrue();
        }

        @Test
        @DisplayName("No encuentra un correo que no existe")
        void unknownEmail() {
            data.customerUser("ana@example.com");

            assertThat(repository.findByEmail("otra@example.com")).isEmpty();
            assertThat(repository.existsByEmail("otra@example.com")).isFalse();
        }
    }

    @Nested
    @DisplayName("Clientes inactivos (findIdleIds)")
    class IdleIds {

        private final LocalDateTime cutoff = T0;
        private final LocalDateTime before = T0.minusDays(1);
        private final LocalDateTime after = T0.plusHours(1);

        @Test
        @DisplayName("Devuelve solo clientes activos sin ingreso ni cambio de estado desde el corte")
        void returnsOnlyIdleActiveCustomers() {
            UUID neverLoggedIn = data.user("a@x.com", CUSTOMER, ACTIVE, before, null, before).getId();
            UUID oldLogin = data.user("b@x.com", CUSTOMER, ACTIVE, before, before, before).getId();
            data.user("recent-login@x.com", CUSTOMER, ACTIVE, before, after, before);
            data.user("reactivated@x.com", CUSTOMER, ACTIVE, before, null, after);
            data.user("admin@x.com", ADMIN, ACTIVE, before, null, before);
            data.user("inactive@x.com", CUSTOMER, INACTIVE, before, null, before);

            List<UUID> ids = repository.findIdleIds("CUSTOMER", "ACTIVE", cutoff);

            assertThat(ids).containsExactlyInAnyOrder(neverLoggedIn, oldLogin);
        }

        @Test
        @DisplayName("Un ingreso o un cambio de estado justo en el corte no cuenta como inactividad")
        void cutoffIsExclusive() {
            data.user("login-at-cutoff@x.com", CUSTOMER, ACTIVE, before, cutoff, before);
            data.user("status-at-cutoff@x.com", CUSTOMER, ACTIVE, before, null, cutoff);

            assertThat(repository.findIdleIds("CUSTOMER", "ACTIVE", cutoff)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Cambio de estado en bloque (moveStatus)")
    class MoveStatus {

        @Test
        @DisplayName("Solo mueve a quien sigue en el estado de origen y registra la fecha del cambio")
        void movesOnlyUsersStillInSourceStatus() {
            User stillActive = data.user("a@x.com", CUSTOMER, ACTIVE, T0.minusDays(5), null, T0.minusDays(5));
            User alreadyInactive = data.user("b@x.com", CUSTOMER, INACTIVE, T0.minusDays(5), null, T0.minusDays(2));
            LocalDateTime now = T0;

            int moved = repository.moveStatus(List.of(stillActive.getId(), alreadyInactive.getId()),
                    data.userStatus(ACTIVE), data.userStatus(INACTIVE), now);
            em.clear();

            assertThat(moved).isEqualTo(1);
            User reloaded = em.find(User.class, stillActive.getId());
            assertThat(reloaded.getStatus().getCode()).isEqualTo("INACTIVE");
            assertThat(reloaded.getStatusChangedAt()).isEqualTo(now);
            assertThat(em.find(User.class, alreadyInactive.getId()).getStatusChangedAt())
                    .isEqualTo(T0.minusDays(2));
        }
    }
}
