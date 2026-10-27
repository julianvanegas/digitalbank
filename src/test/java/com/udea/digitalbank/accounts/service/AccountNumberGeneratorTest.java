package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.repository.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountNumberGenerator")
class AccountNumberGeneratorTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountNumberPermutation permutation;

    @Test
    @DisplayName("HU10 CA06 / HU28 CA07 - next - convierte el siguiente valor de la secuencia con la permutación, sin consultar si existe")
    void next_deberiaPermutarElSiguienteValorDeLaSecuencia() {
        // Arrange
        when(accountRepository.nextAccountNumberValue()).thenReturn(7L);
        when(permutation.apply(7L)).thenReturn("523456789012");

        // Act
        String number = new AccountNumberGenerator(accountRepository, permutation).next();

        // Assert
        assertThat(number).isEqualTo("523456789012");
        verify(accountRepository).nextAccountNumberValue();
        verifyNoMoreInteractions(accountRepository); // ni una consulta de existencia
    }
}
