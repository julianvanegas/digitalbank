package com.udea.digitalbank.transactions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.udea.digitalbank.transactions.service.TransactionReferenceGenerator.ALPHABET;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TransactionReferenceGenerator")
class TransactionReferenceGeneratorTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 17, 3, 15);
    private static final String CODE_REGEX = "[1-9A-HJ-NP-Za-km-z]{22}";

    private final TransactionReferenceGenerator generator = new TransactionReferenceGenerator();

    // Lo inverso del generador: del texto de 22 caracteres de vuelta al UUID
    private static UUID decode(String code) {
        BigInteger value = BigInteger.ZERO;
        for (char c : code.toCharArray()) {
            value = value.multiply(BigInteger.valueOf(ALPHABET.length())).add(BigInteger.valueOf(ALPHABET.indexOf(c)));
        }
        byte[] raw = value.toByteArray();
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.position(16 - Math.min(raw.length, 16));
        buffer.put(raw, Math.max(0, raw.length - 16), Math.min(raw.length, 16));
        buffer.flip();
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    @Test
    @DisplayName("HU20 CA01 - Tiene la forma TRF-AAAAMMDD-{22 caracteres}, solo letras y dígitos sin 0/O/I/l, y cabe en la columna")
    void deberiaTenerLaFormaDelComprobante() {
        String reference = generator.forTransfer(UUID.randomUUID(), CREATED_AT);

        assertThat(reference).matches("TRF-20261017-" + CODE_REGEX);
        assertThat(reference).hasSize(35);
        // Ningún guion ni símbolo dentro del código: solo los dos separadores del formato
        assertThat(reference.chars().filter(c -> c == '-').count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Transversal - El alfabeto son 58 símbolos alfanuméricos distintos, sin los que se confunden")
    void deberiaUsarUnAlfabetoSinAmbiguedades() {
        assertThat(ALPHABET).hasSize(58);
        assertThat(ALPHABET.chars().distinct().count()).isEqualTo(58);
        assertThat(ALPHABET).matches("[A-Za-z0-9]+").doesNotContain("0", "O", "I", "l");
    }

    @Test
    @DisplayName("Transversal - Se deriva del id: el mismo UUID y fecha dan siempre la misma referencia")
    void deberiaSerDeterministaPorId() {
        UUID id = UUID.randomUUID();

        assertThat(generator.forTransfer(id, CREATED_AT)).isEqualTo(generator.forTransfer(id, CREATED_AT));
    }

    @Test
    @DisplayName("Transversal - Conserva el UUID completo: del código se recupera exactamente el mismo id (500 sorteados)")
    void deberiaPoderRecuperarElUuidCompleto() {
        for (int i = 0; i < 500; i++) {
            UUID id = UUID.randomUUID();
            String code = generator.forTransfer(id, CREATED_AT).substring("TRF-20261017-".length());

            assertThat(decode(code)).isEqualTo(id);
        }
    }

    @Test
    @DisplayName("Transversal - Extremos: el UUID en cero, el más pequeño, y el de todos los bits en uno dan 22 caracteres válidos")
    void deberiaCodificarLosExtremosConLargoFijo() {
        String zero = generator.forTransfer(new UUID(0L, 0L), CREATED_AT);
        String one = generator.forTransfer(new UUID(0L, 1L), CREATED_AT);
        String max = generator.forTransfer(new UUID(-1L, -1L), CREATED_AT);

        // El cero se rellena con el primer símbolo del alfabeto para que el largo no cambie
        assertThat(zero).isEqualTo("TRF-20261017-1111111111111111111111");
        assertThat(one).isEqualTo("TRF-20261017-1111111111111111111112");
        assertThat(max).matches("TRF-20261017-" + CODE_REGEX);
        assertThat(decode(max.substring(13))).isEqualTo(new UUID(-1L, -1L));
    }

    @Test
    @DisplayName("HU20 CA01 - UUID distintos dan referencias distintas (50.000 sorteados sin repetir)")
    void noDeberiaRepetirReferencias() {
        Set<String> references = new HashSet<>();
        for (int i = 0; i < 50_000; i++) {
            references.add(generator.forTransfer(UUID.randomUUID(), CREATED_AT));
        }

        assertThat(references).hasSize(50_000);
    }

    @Test
    @DisplayName("Transversal - La fecha es la de creación que se pasa, no la de hoy")
    void deberiaUsarLaFechaDeCreacion() {
        String reference = generator.forTransfer(UUID.randomUUID(), LocalDateTime.of(2025, 1, 2, 23, 59));

        assertThat(reference).startsWith("TRF-20250102-");
    }
}
