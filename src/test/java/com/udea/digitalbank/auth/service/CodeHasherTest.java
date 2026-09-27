package com.udea.digitalbank.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de {@link CodeHasher}. No requiere mocks: es una función pura de HMAC-SHA256,
 * así que aquí se prueba el algoritmo real (antes solo se mockeaba desde VerificationServiceTest).
 */
class CodeHasherTest {

    private final CodeHasher codeHasher = new CodeHasher("una-clave-secreta-de-mas-de-32-caracteres");

    @Test
    @DisplayName("El mismo código, usuario y propósito siempre produce el mismo hash (determinista)")
    void deberiaSerDeterministaParaLosMismosDatos() {
        // Arrange & Act
        String hash1 = codeHasher.hash(1L, (short) 1, "123456");
        String hash2 = codeHasher.hash(1L, (short) 1, "123456");

        // Assert
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("matches() acepta el hash correcto para el mismo (usuario, propósito, código)")
    void matchesDeberiaAceptarElHashCorrecto() {
        // Arrange
        String hash = codeHasher.hash(1L, (short) 1, "123456");

        // Act & Assert
        assertThat(codeHasher.matches(1L, (short) 1, "123456", hash)).isTrue();
    }

    @Test
    @DisplayName("matches() rechaza cuando el código no coincide")
    void matchesDeberiaRechazarUnCodigoDistinto() {
        // Arrange
        String hash = codeHasher.hash(1L, (short) 1, "123456");

        // Act & Assert
        assertThat(codeHasher.matches(1L, (short) 1, "000000", hash)).isFalse();
    }

    @Test
    @DisplayName("El mismo código produce hashes distintos para usuarios distintos")
    void deberiaProducirHashesDistintosParaUsuariosDistintos() {
        // Arrange & Act
        String hashUsuario1 = codeHasher.hash(1L, (short) 1, "123456");
        String hashUsuario2 = codeHasher.hash(2L, (short) 1, "123456");

        // Assert
        assertThat(hashUsuario1).isNotEqualTo(hashUsuario2);
    }

    @Test
    @DisplayName("El mismo código produce hashes distintos para propósitos distintos (LOGIN vs PASSWORD_RESET)")
    void deberiaProducirHashesDistintosParaPropositosDistintos() {
        // Arrange & Act
        String hashLogin = codeHasher.hash(1L, (short) 1, "123456");
        String hashReset = codeHasher.hash(1L, (short) 3, "123456");

        // Assert
        assertThat(hashLogin).isNotEqualTo(hashReset);
    }

    @Test
    @DisplayName("matches() rechaza un hash de un usuario/propósito distinto aunque el código sea el mismo")
    void matchesDeberiaRechazarUnHashCalculadoParaOtroUsuario() {
        // Arrange
        String hashUsuario2 = codeHasher.hash(2L, (short) 1, "123456");

        // Act & Assert: se intenta validar el código para el usuario 1 con el hash del usuario 2
        assertThat(codeHasher.matches(1L, (short) 1, "123456", hashUsuario2)).isFalse();
    }
}