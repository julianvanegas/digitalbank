package com.udea.digitalbank.accounts.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AccountNumberPermutation")
class AccountNumberPermutationTest {

    private static final String SECRET = "test-only-account-secret-with-32-plus-chars";
    private static final BigInteger N = BigInteger.valueOf(AccountNumberPermutation.SIZE);

    private final AccountNumberPermutation permutation = new AccountNumberPermutation(SECRET);

    @Test
    @DisplayName("Cada número tiene 12 dígitos y no empieza por cero, incluso en los extremos de la secuencia")
    void deberiaGenerarNumerosValidosEnTodoElRango() {
        long[] samples = {1, 2, 3, 999_999, 450_000_000_000L, AccountNumberPermutation.SIZE - 1,
                AccountNumberPermutation.SIZE};

        for (long n : samples) {
            assertThat(permutation.apply(n)).as("n=%d", n).matches("[1-9][0-9]{11}");
        }
    }

    @Test
    @DisplayName("Valores consecutivos de la secuencia no dan números únicos repetidos (100.000 distintos)")
    void noDeberiaRepetirNumerosEnValoresConsecutivos() {
        Set<String> numbers = new HashSet<>();

        for (long n = 1; n <= 100_000; n++) {
            numbers.add(permutation.apply(n));
        }

        assertThat(numbers).hasSize(100_000);
    }

    @Test
    @DisplayName("Es una permutación: el multiplicador es coprimo con el tamaño del rango y tiene inversa")
    void deberiaSerBiyectiva() {
        BigInteger multiplier = (BigInteger) ReflectionTestUtils.getField(permutation, "multiplier");
        BigInteger offset = (BigInteger) ReflectionTestUtils.getField(permutation, "offset");
        BigInteger inverse = multiplier.modInverse(N); // solo existe si gcd(K, N) = 1

        for (long n : new long[]{1, 7, 123_456_789, 899_999_999_999L, AccountNumberPermutation.SIZE}) {
            BigInteger position = new BigInteger(permutation.apply(n)).subtract(
                    BigInteger.valueOf(AccountNumberPermutation.FIRST));
            BigInteger recovered = position.subtract(offset).multiply(inverse).mod(N);
            // El valor máximo de la secuencia (N) equivale a 0 módulo N
            assertThat(recovered).as("n=%d", n).isEqualTo(BigInteger.valueOf(n).mod(N));
        }
    }

    @Test
    @DisplayName("No es secuencial: números contiguos de la secuencia no producen cuentas contiguas")
    void noDeberiaDejarDeducirLaCuentaContigua() {
        for (long n = 1; n <= 50; n++) {
            long gap = Math.abs(Long.parseLong(permutation.apply(n + 1)) - Long.parseLong(permutation.apply(n)));
            assertThat(gap).as("n=%d", n).isGreaterThan(1_000_000L);
        }
    }

    @Test
    @DisplayName("Mismo secreto, mismos números; otro secreto, otros números")
    void deberiaDependerDelSecreto() {
        AccountNumberPermutation same = new AccountNumberPermutation(SECRET);
        AccountNumberPermutation other = new AccountNumberPermutation("otro-secreto-de-prueba-con-mas-de-32-caracteres");

        assertThat(same.apply(42)).isEqualTo(permutation.apply(42));
        assertThat(other.apply(42)).isNotEqualTo(permutation.apply(42));
    }

    @Test
    @DisplayName("Un valor de la secuencia fuera de rango es un error de programación")
    void deberiaRechazarValoresFueraDeRango() {
        assertThatThrownBy(() -> permutation.apply(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> permutation.apply(-5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> permutation.apply(AccountNumberPermutation.SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
