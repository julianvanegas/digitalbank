package com.udea.digitalbank.transactions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de la referencia de los retiros (HU19), {@link TransactionReferenceGenerator#forWithdrawal}. Cubren lo mismo
 * que TransactionReferenceGeneratorTest cubre para las otras referencias: forma, prefijo, fecha y unicidad. El alfabeto
 * y la codificación del UUID son comunes a los tres tipos y ya están probados allí.
 */
@DisplayName("HU19 - Referencia del comprobante de un retiro")
class TransactionReferenceGeneratorHuTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 17, 3, 15);
    private static final String CODE_REGEX = "[1-9A-HJ-NP-Za-km-z]{22}";

    private final TransactionReferenceGenerator generator = new TransactionReferenceGenerator();

    @Test
    @DisplayName("HU19 - Tiene la forma RET-AAAAMMDD-{22 caracteres} y cabe en la columna")
    void deberiaTenerLaFormaDelComprobante() {
        String reference = generator.forWithdrawal(UUID.randomUUID(), CREATED_AT);

        assertThat(reference).matches("RET-20261017-" + CODE_REGEX).hasSize(35);
    }

    @Test
    @DisplayName("HU19 - La fecha es la de creación que se pasa, no la de hoy")
    void deberiaUsarLaFechaDeCreacion() {
        String reference = generator.forWithdrawal(UUID.randomUUID(), LocalDateTime.of(2025, 1, 2, 23, 59));

        assertThat(reference).startsWith("RET-20250102-");
    }

    @Test
    @DisplayName("HU19 - UUID distintos dan referencias distintas (50.000 sorteados sin repetir)")
    void noDeberiaRepetirReferencias() {
        Set<String> references = new HashSet<>();
        for (int i = 0; i < 50_000; i++) {
            references.add(generator.forWithdrawal(UUID.randomUUID(), CREATED_AT));
        }

        assertThat(references).hasSize(50_000);
    }

    // Determinista a propósito: la referencia lleva a su fila. Si alguien sorteara la parte variable, esto fallaría
    @Test
    @DisplayName("HU19 - Se deriva del id: el mismo UUID y fecha dan siempre la misma referencia")
    void deberiaSerDeterministaPorId() {
        UUID id = UUID.randomUUID();

        assertThat(generator.forWithdrawal(id, CREATED_AT)).isEqualTo(generator.forWithdrawal(id, CREATED_AT));
    }
}
