package com.udea.digitalbank.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * HMAC-SHA256 con clave del servidor. Un SHA-256 simple no protege un código de 6 dígitos:
 * solo hay un millón de combinaciones y se revertiría al instante si la base se filtra.
 */
@Component
public class CodeHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] key;

    public CodeHasher(@Value("${verification.hmac-secret}") String secret) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    // La cuenta y el propósito van en el mensaje: el mismo código produce hashes distintos por reto
    public String hash(Long accountId, short purposeId, String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            byte[] digest = mac.doFinal((accountId + ":" + purposeId + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el HMAC del código", e);
        }
    }

    public boolean matches(Long accountId, short purposeId, String code, String expectedHash) {
        String actual = hash(accountId, purposeId, code);
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8), expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}
