package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.domain.VerificationChallenge;
import com.udea.digitalbank.support.integration.RepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.udea.digitalbank.support.integration.TestData.LOGIN;
import static com.udea.digitalbank.support.integration.TestData.PASSWORD_RESET;
import static com.udea.digitalbank.support.integration.TestData.T0;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VerificationChallengeRepository contra PostgreSQL")
class VerificationChallengeRepositoryIT extends RepositoryTest {

    @Autowired
    private VerificationChallengeRepository repository;

    @Test
    @DisplayName("Bloquea y devuelve el código solo si coincide el propósito")
    void lockByIdAndPurpose() {
        User user = data.customerUser("ana@example.com");
        VerificationChallenge login = data.challenge(user, LOGIN, T0.plusMinutes(5));

        assertThat(repository.lockByIdAndPurpose(login.getId(), "LOGIN")).isPresent();
        assertThat(repository.lockByIdAndPurpose(login.getId(), "PASSWORD_RESET")).isEmpty();
    }

    @Test
    @DisplayName("Bloquea y devuelve el código del usuario para el propósito pedido")
    void lockByUserAndPurpose() {
        User user = data.customerUser("ana@example.com");
        data.challenge(user, LOGIN, T0.plusMinutes(5));
        VerificationChallenge reset = data.challenge(user, PASSWORD_RESET, T0.plusMinutes(5));

        assertThat(repository.lockByUserAndPurpose(user.getId(), "PASSWORD_RESET")).get()
                .extracting(VerificationChallenge::getId).isEqualTo(reset.getId());
    }

    @Test
    @DisplayName("deleteByUserAndPurpose borra solo el código de ese propósito")
    void deleteByUserAndPurpose() {
        User user = data.customerUser("ana@example.com");
        data.challenge(user, LOGIN, T0.plusMinutes(5));
        VerificationChallenge reset = data.challenge(user, PASSWORD_RESET, T0.plusMinutes(5));

        repository.deleteByUserAndPurpose(user.getId(), "LOGIN");
        em.clear();

        assertThat(repository.findAll()).extracting(VerificationChallenge::getId).containsExactly(reset.getId());
    }

    @Test
    @DisplayName("deleteExpired borra los códigos vencidos y conserva el que vence justo ahora")
    void deleteExpired() {
        User a = data.customerUser("a@example.com");
        User b = data.customerUser("b@example.com");
        data.challenge(a, LOGIN, T0.minusSeconds(1));
        VerificationChallenge atLimit = data.challenge(b, LOGIN, T0);

        int deleted = repository.deleteExpired(T0);
        em.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findAll()).extracting(VerificationChallenge::getId).containsExactly(atLimit.getId());
    }
}
