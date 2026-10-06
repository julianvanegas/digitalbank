package com.udea.digitalbank.auth.service;

import java.util.UUID;

// Resultado de reenviar el código de un reto: a quién va, el código nuevo y lo que le queda al cliente
public record ResentChallenge(UUID userId, IssuedChallenge challenge, int remainingResends, long retryAfterSeconds) {
}
