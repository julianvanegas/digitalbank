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

    // definido en application.properties: jwt.secret (32+ caracteres)
    @Value("${jwt.secret}")
    private String secret;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // el jti sirve para invalidar sesiones anteriores (ver AuthService.verifyTwoFactor)
    // El token no lleva exp: la vigencia (inactividad) la decide la fila de auth_sessions, que se renueva con la actividad
    public String generateToken(UUID userId, String role, String jti, Date issuedAt) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .id(jti)
                .issuedAt(issuedAt)
                .signWith(signingKey())
                .compact();
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

    // devuelve false si la firma no coincide o el token está mal formado (parseClaims lanza excepción)
    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
