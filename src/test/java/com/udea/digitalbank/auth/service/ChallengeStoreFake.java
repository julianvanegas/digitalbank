package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.VerificationChallenge;
import com.udea.digitalbank.auth.repository.VerificationChallengeRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Apoyo de pruebas (no es una prueba): hace que un mock de {@link VerificationChallengeRepository} se comporte
 * como la tabla verification_challenges en memoria, con su restricción de un reto por usuario y propósito.
 * Así las pruebas de flujo encadenan operaciones reales (emitir, fallar, reenviar, reutilizar) y comprueban el
 * estado final en vez de verificar cada llamada suelta. Los stubs son lenient porque cada prueba usa una parte.
 */
final class ChallengeStoreFake {

    private final Map<UUID, VerificationChallenge> challenges = new HashMap<>();

    ChallengeStoreFake(VerificationChallengeRepository repository) {
        lenient().when(repository.save(any(VerificationChallenge.class))).thenAnswer(invocation -> {
            VerificationChallenge challenge = invocation.getArgument(0);
            challenges.put(challenge.getId(), challenge);
            return challenge;
        });

        lenient().doAnswer(invocation -> {
            VerificationChallenge challenge = invocation.getArgument(0);
            challenges.remove(challenge.getId());
            return null;
        }).when(repository).delete(any(VerificationChallenge.class));

        lenient().doAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            String purpose = invocation.getArgument(1);
            challenges.values().removeIf(c -> c.getUserId().equals(userId)
                    && c.getPurpose().getPurpose().equals(purpose));
            return null;
        }).when(repository).deleteByUserAndPurpose(any(UUID.class), anyString());

        lenient().when(repository.lockByIdAndPurpose(any(UUID.class), anyString())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            String purpose = invocation.getArgument(1);
            return Optional.ofNullable(challenges.get(id))
                    .filter(c -> c.getPurpose().getPurpose().equals(purpose));
        });

        lenient().when(repository.lockByUserAndPurpose(any(UUID.class), anyString())).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            String purpose = invocation.getArgument(1);
            return challenges.values().stream()
                    .filter(c -> c.getUserId().equals(userId) && c.getPurpose().getPurpose().equals(purpose))
                    .findFirst();
        });
    }

    /** Reto guardado con ese id, o null si ya no existe (se consumió, se invalidó o se reemplazó). */
    VerificationChallenge get(UUID id) {
        return challenges.get(id);
    }

    /** Simula que pasó el tiempo: deja el reto vencido hace un segundo. */
    void expire(UUID id) {
        challenges.get(id).setExpiresAt(java.time.LocalDateTime.now().minusSeconds(1));
    }

    int size() {
        return challenges.size();
    }
}
