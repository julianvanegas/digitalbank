package com.udea.digitalbank.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias de {@link JwtUtil}. Sin mocks: se genera y valida un JWT real, porque es
 * exactamente el comportamiento de seguridad que se debe garantizar (nadie debería confiar en un
 * doble de esta clase para algo tan sensible como la firma de tokens).
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // @Value se resuelve normalmente vía Spring; aquí se fija por reflexión al no levantar contexto.
        setField(jwtUtil, "secret", "clave-de-prueba-con-mas-de-32-caracteres-para-hmac");
        setField(jwtUtil, "expirationMs", 900_000L); // 15 minutos
        jwtUtil.init(); // lo que Spring hace al crear el bean (@PostConstruct)
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
    @DisplayName("Transversal - init - el secreto se valida al arrancar")
    class SecretValidation {

        private JwtUtil withSecret(String secret) {
            JwtUtil util = new JwtUtil();
            setField(util, "secret", secret);
            setField(util, "expirationMs", 900_000L);
            return util;
        }

        @Test
        @DisplayName("Transversal - Un secreto de menos de 32 bytes impide que la app arranque, con un mensaje claro")
        void deberiaRechazarUnSecretoCorto() {
            assertThatThrownBy(() -> withSecret("a".repeat(31)).init())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jwt.secret")
                    .hasMessageContaining("32")
                    .hasMessageContaining("tiene 31");
        }

        @Test
        @DisplayName("Transversal - Un secreto ausente o vacío también se rechaza")
        void deberiaRechazarUnSecretoAusenteOVacio() {
            assertThatThrownBy(() -> withSecret(null).init()).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> withSecret("").init()).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Transversal - Exactamente 32 bytes es suficiente y el token se puede firmar y leer")
        void deberiaAceptarExactamente32Bytes() {
            JwtUtil util = withSecret("a".repeat(32));
            util.init();

            Date now = new Date();
            UUID userId = UUID.randomUUID();
            String token = util.generateToken(userId, "ADMIN", UUID.randomUUID().toString(), now,
                    new Date(now.getTime() + 60_000));

            assertThat(util.tryParse(token)).isPresent();
        }

        @Test
        @DisplayName("Transversal - Se miden bytes UTF-8, no caracteres: 20 letras ñ son 40 bytes y bastan")
        void deberiaMedirBytesYNoCaracteres() {
            String secret = "ñ".repeat(20);
            assertThat(secret.length()).isEqualTo(20);

            JwtUtil util = withSecret(secret);
            util.init();

            assertThat(util.tryParse(util.generateToken(UUID.randomUUID(), "CUSTOMER",
                    UUID.randomUUID().toString(), new Date(), new Date(System.currentTimeMillis() + 60_000)))).isPresent();
        }
    }

    @Nested
    @DisplayName("HU02 / HU04 - generateToken / parseClaims - round-trip del token")
    class GenerateAndParse {

        @Test
        @DisplayName("HU04 CA05 - Un token recién generado se puede parsear y contiene el subject, el rol y el jti correctos")
        void deberiaGenerarUnTokenQueSePuedeParsearCorrectamente() {
            // Arrange
            Date issuedAt = new Date();
            Date expiresAt = new Date(issuedAt.getTime() + 900_000L);
            UUID userId = UUID.randomUUID();

            // Act
            String token = jwtUtil.generateToken(userId, "CUSTOMER", "jti-123", issuedAt, expiresAt);
            var claims = jwtUtil.parseClaims(token);

            // Assert
            assertThat(claims.getSubject()).isEqualTo(userId.toString());
            assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
            assertThat(claims.getId()).isEqualTo("jti-123");
        }

        @Test
        @DisplayName("Transversal - getExpirationMs() devuelve el valor configurado")
        void deberiaExponerLaExpiracionConfigurada() {
            assertThat(jwtUtil.getExpirationMs()).isEqualTo(900_000L);
        }

        @Test
        @DisplayName("HU02 CA06 - generateJti() produce identificadores distintos en cada llamada")
        void deberiaGenerarJtisDistintosEnCadaLlamada() {
            assertThat(jwtUtil.generateJti()).isNotEqualTo(jwtUtil.generateJti());
        }
    }

    @Nested
    @DisplayName("HU02 / HU04 - tryParse - token expirado o alterado no debe validar")
    class IsValid {

        @Test
        @DisplayName("HU04 CA05 - Un token recién emitido y vigente es válido")
        void deberiaSerValidoUnTokenVigente() {
            // Arrange
            Date issuedAt = new Date();
            Date expiresAt = new Date(issuedAt.getTime() + 900_000L);
            String token = jwtUtil.generateToken(UUID.randomUUID(), "CUSTOMER", "jti-1", issuedAt, expiresAt);

            // Act & Assert
            assertThat(jwtUtil.tryParse(token)).isPresent();
        }

        @Test
        @DisplayName("HU02 CA08 / HU04 CA03 - Un token con fecha de expiración en el pasado no es válido")
        void noDeberiaSerValidoUnTokenExpirado() {
            // Arrange: se genera ya vencido (con 1 minuto de vencido)
            Date issuedAt = new Date(System.currentTimeMillis() - 120_000L);
            Date expiresAt = new Date(System.currentTimeMillis() - 60_000L);
            String token = jwtUtil.generateToken(UUID.randomUUID(), "CUSTOMER", "jti-1", issuedAt, expiresAt);

            // Act & Assert
            assertThat(jwtUtil.tryParse(token)).isEmpty();
        }

        @Test
        @DisplayName("HU04 CA04 - Un token firmado con otra clave (alterado/falsificado) no es válido")
        void noDeberiaSerValidoUnTokenFirmadoConOtraClave() {
            // Arrange: mismo contenido, pero firmado por una instancia con una clave distinta
            JwtUtil otraInstancia = new JwtUtil();
            setField(otraInstancia, "secret", "otra-clave-completamente-distinta-de-32-caracteres");
            setField(otraInstancia, "expirationMs", 900_000L);
            otraInstancia.init();
            Date issuedAt = new Date();
            Date expiresAt = new Date(issuedAt.getTime() + 900_000L);
            String tokenFalsificado = otraInstancia.generateToken(UUID.randomUUID(), "CUSTOMER", "jti-1", issuedAt, expiresAt);

            // Act & Assert
            assertThat(jwtUtil.tryParse(tokenFalsificado)).isEmpty();
        }

        @Test
        @DisplayName("HU04 CA04 - Un texto que no es un JWT no es válido")
        void noDeberiaSerValidoUnTextoQueNoEsUnToken() {
            assertThat(jwtUtil.tryParse("esto-no-es-un-token")).isEmpty();
        }
    }
}