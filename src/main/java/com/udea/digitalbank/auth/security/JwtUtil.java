package com.udea.digitalbank.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    // definidos en application.properties: jwt.secret (32+ caracteres) y jwt.expiration-ms
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // el jti sirve para invalidar sesiones anteriores (ver AuthService.verifyTwoFactor)
    // issuedAt y expiresAt vienen de fuera para que el token y la fila de auth_session coincidan
    public String generateToken(Long accountId, String role, String jti, Date issuedAt, Date expiresAt) {
        return Jwts.builder()
                .subject(String.valueOf(accountId))
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

    // devuelve false si la firma no coincide o el token expiró (parseClaims lanza excepción en ambos casos)
    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
