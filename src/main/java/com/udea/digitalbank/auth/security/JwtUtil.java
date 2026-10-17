package com.udea.digitalbank.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final int MIN_SECRET_BYTES = 32;

    /** Valores definidos en application.properties: {@code jwt.secret} (32+ caracteres) y {@code jwt.expiration-ms}. */
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey signingKey;

    /**
     * Valida el secreto al arrancar y arma la clave una sola vez. HS256 exige al menos 256 bits: sin esta
     * comprobación un secreto corto solo fallaría en la primera petición con un token, y no al iniciar la app.
     * Se mide en bytes UTF-8, que es como se firma, y no en caracteres.
     */
    @PostConstruct
    void init() {
        byte[] keyBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("jwt.secret debe tener al menos " + MIN_SECRET_BYTES
                    + " bytes (256 bits) para firmar con HS256; tiene " + keyBytes.length);
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private SecretKey signingKey() {
        return signingKey;
    }

    /**
     * El {@code jti} sirve para invalidar sesiones anteriores (ver {@code AuthService.verifyTwoFactor}). Las fechas
     * {@code issuedAt} y {@code expiresAt} vienen de fuera para que el token y la fila de {@code auth_session}
     * coincidan.
     */
    public String generateToken(UUID userId, String role, String jti, Date issuedAt, Date expiresAt) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .id(jti)
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(signingKey())
                .compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public String generateJti() {
        return UUID.randomUUID().toString();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Vacío si la firma no coincide, el token ha expirado o está mal formado: parsea y verifica una sola vez. */
    public Optional<Claims> tryParse(String token) {
        try {
            return Optional.of(parseClaims(token));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
