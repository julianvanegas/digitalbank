package com.udea.digitalbank.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de {@link JwtUtil}. Sin mocks: se genera y valida un JWT real, porque es
 * exactamente el comportamiento de seguridad que hay que garantizar (nadie debería confiar en un
 * doble de esta clase para algo tan sensible como la firma de tokens).
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // @Value se resuelve normalmente vía Spring; aquí se fija por reflexión al no levantar contexto.
        setField(jwtUtil, "secret", "clave-de-prueba-con-mas-de-32-caracteres-para-hmac");
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("generateToken / parseClaims - round-trip del token")
    class GenerateAndParse {

        @Test
        @DisplayName("Un token recién generado se puede parsear y contiene el subject, el rol y el jti correctos")
        void deberiaGenerarUnTokenQueSePuedeParsearCorrectamente() {
            // Arrange
            Date issuedAt = new Date();
            UUID userId = UUID.randomUUID();

            // Act
            String token = jwtUtil.generateToken(userId, "CUSTOMER", "jti-123", issuedAt);
            var claims = jwtUtil.parseClaims(token);

            // Assert
            assertThat(claims.getSubject()).isEqualTo(userId.toString());
            assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
            assertThat(claims.getId()).isEqualTo("jti-123");
        }

        @Test
        @DisplayName("El token no lleva exp: la vigencia la decide la sesión, no el reloj del token")
        void deberiaGenerarUnTokenSinExpiracion() {
            String token = jwtUtil.generateToken(UUID.randomUUID(), "CUSTOMER", "jti-1", new Date());

            assertThat(jwtUtil.parseClaims(token).getExpiration()).isNull();
        }

        @Test
        @DisplayName("generateJti() produce identificadores distintos en cada llamada")
        void deberiaGenerarJtisDistintosEnCadaLlamada() {
            assertThat(jwtUtil.generateJti()).isNotEqualTo(jwtUtil.generateJti());
        }
    }

    @Nested
    @DisplayName("isValid - CA09/CA10 de HU02/HU04 (token alterado no debe validar)")
    class IsValid {

        @Test
        @DisplayName("Un token recién emitido y vigente es válido")
        void deberiaSerValidoUnTokenVigente() {
            // Arrange
            String token = jwtUtil.generateToken(UUID.randomUUID(), "CUSTOMER", "jti-1", new Date());

            // Act & Assert
            assertThat(jwtUtil.isValid(token)).isTrue();
        }

        @Test
        @DisplayName("Un token emitido hace horas sigue siendo válido para el JWT: la inactividad se comprueba en la sesión")
        void deberiaSerValidoUnTokenAntiguoPorqueNoLlevaExp() {
            Date issuedAt = new Date(System.currentTimeMillis() - 3 * 3_600_000L);
            String token = jwtUtil.generateToken(UUID.randomUUID(), "CUSTOMER", "jti-1", issuedAt);

            assertThat(jwtUtil.isValid(token)).isTrue();
        }

        @Test
        @DisplayName("Un token firmado con otra clave (alterado/falsificado) no es válido")
        void noDeberiaSerValidoUnTokenFirmadoConOtraClave() {
            // Arrange: mismo contenido, pero firmado por una instancia con una clave distinta
            JwtUtil otraInstancia = new JwtUtil();
            setField(otraInstancia, "secret", "otra-clave-completamente-distinta-de-32-caracteres");
            String tokenFalsificado = otraInstancia.generateToken(UUID.randomUUID(), "CUSTOMER", "jti-1", new Date());

            // Act & Assert
            assertThat(jwtUtil.isValid(tokenFalsificado)).isFalse();
        }

        @Test
        @DisplayName("Un texto que no es un JWT no es válido")
        void noDeberiaSerValidoUnTextoQueNoEsUnToken() {
            assertThat(jwtUtil.isValid("esto-no-es-un-token")).isFalse();
        }
    }
}