package com.udea.digitalbank.auth.service;

import java.util.UUID;

// El código en claro solo existe aquí, para enviarlo por correo; en base solo queda su HMAC
public record IssuedChallenge(UUID challengeId, String code, int ttlMinutes) {
}
