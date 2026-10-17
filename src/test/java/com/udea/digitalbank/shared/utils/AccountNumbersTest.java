package com.udea.digitalbank.shared.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AccountNumbers")
class AccountNumbersTest {

    @Test
    @DisplayName("lastDigits - deja solo los últimos 4 dígitos de un número de 12")
    void deberiaDejarLosUltimosCuatroDigitos() {
        assertThat(AccountNumbers.lastDigits("123456789012")).isEqualTo("9012");
    }

    @Test
    @DisplayName("lastDigits - un número de 4 dígitos o menos se devuelve entero")
    void deberiaDevolverEnteroUnNumeroCorto() {
        assertThat(AccountNumbers.lastDigits("1234")).isEqualTo("1234");
        assertThat(AccountNumbers.lastDigits("12")).isEqualTo("12");
    }
}
