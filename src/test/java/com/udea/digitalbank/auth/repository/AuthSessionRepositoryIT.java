package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.AuthSession;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.support.integration.RepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.udea.digitalbank.support.integration.TestData.CUSTOMER;
import static com.udea.digitalbank.support.integration.TestData.INACTIVE;
import static com.udea.digitalbank.support.integration.TestData.T0;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthSessionRepository contra PostgreSQL")
class AuthSessionRepositoryIT extends RepositoryTest {

    private static final LocalDateTime NOW = T0;
    private static final LocalDateTime RENEWED = T0.plusMinutes(15);

    @Autowired
    private AuthSessionRepository repository;

    @Nested
    @DisplayName("Validar y renovar la sesión (touch)")
    class Touch {

        private User user;
        private final UUID jti = UUID.randomUUID();

        @BeforeEach
        void activeUser() {
            user = data.customerUser("ana@example.com");
        }

        @Test
        @DisplayName("Renueva una sesión vigente del mismo jti y usuario activo")
        void renewsValidSession() {
            data.session(user, jti, NOW.plusMinutes(5));

            int touched = repository.touch(user.getId(), jti, NOW, RENEWED, "ACTIVE");
            em.clear();

            assertThat(touched).isEqualTo(1);
            assertThat(em.find(AuthSession.class, user.getId()).getExpiresAt()).isEqualTo(RENEWED);
        }

        @Test
        @DisplayName("No renueva si el token es de otra sesión (otro jti)")
        void rejectsOtherJti() {
            data.session(user, jti, NOW.plusMinutes(5));

            assertThat(repository.touch(user.getId(), UUID.randomUUID(), NOW, RENEWED, "ACTIVE")).isZero();
        }

        @Test
        @DisplayName("No renueva una sesión que vence justo ahora")
        void rejectsExpiredSession() {
            data.session(user, jti, NOW);

            assertThat(repository.touch(user.getId(), jti, NOW, RENEWED, "ACTIVE")).isZero();
        }

        @Test
        @DisplayName("No renueva si el usuario ya no está en el estado exigido")
        void rejectsUserInOtherStatus() {
            User inactive = data.user("inactivo@example.com", CUSTOMER, INACTIVE, T0, null, T0);
            data.session(inactive, jti, NOW.plusMinutes(5));

            assertThat(repository.touch(inactive.getId(), jti, NOW, RENEWED, "ACTIVE")).isZero();
        }

        @Test
        @DisplayName("No renueva si no hay sesión")
        void rejectsMissingSession() {
            assertThat(repository.touch(user.getId(), jti, NOW, RENEWED, "ACTIVE")).isZero();
        }
    }

    @Test
    @DisplayName("deleteExpired borra solo las sesiones vencidas")
    void deleteExpiredRemovesOnlyExpired() {
        User expired = data.customerUser("a@example.com");
        User valid = data.customerUser("b@example.com");
        data.session(expired, UUID.randomUUID(), NOW.minusSeconds(1));
        data.session(valid, UUID.randomUUID(), NOW.plusMinutes(1));

        int deleted = repository.deleteExpired(NOW);
        em.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findAll()).extracting(AuthSession::getUserId).containsExactly(valid.getId());
    }

    @Test
    @DisplayName("deleteByUsers borra las sesiones de los usuarios indicados y no las demás")
    void deleteByUsersRemovesOnlyThoseUsers() {
        User a = data.customerUser("a@example.com");
        User b = data.customerUser("b@example.com");
        User c = data.customerUser("c@example.com");
        List.of(a, b, c).forEach(u -> data.session(u, UUID.randomUUID(), NOW.plusMinutes(5)));

        repository.deleteByUsers(List.of(a.getId(), b.getId()));
        em.clear();

        assertThat(repository.findAll()).extracting(AuthSession::getUserId).containsExactly(c.getId());
    }
}
