package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link AccountKeyGenerator}: la llave visible de la cuenta.
 * Formato (HU10 CA01 ajustada): @ + dos primeras letras del primer nombre, en mayúscula y sin tildes + tres últimos
 * dígitos del documento + el primer consecutivo libre para ese prefijo. Ejemplo: @MA1231.
 * Las llaves ya tomadas se simulan con el repositorio; el CHECK de la base exige ^@[A-Z]{2}[0-9]{3}[1-9][0-9]*$.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU10 / HU28 - AccountKeyGenerator")
class AccountKeyGeneratorTest {

    private static final String DB_KEY_FORMAT = "^@[A-Z]{2}[0-9]{3}[1-9][0-9]*$";

    @Mock
    private AccountRepository accountRepository;

    private AccountKeyGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new AccountKeyGenerator(accountRepository);
    }

    @Test
    @DisplayName("HU10 CA01 / HU28 CA01 - Primera cuenta: @ + MA + 123 + consecutivo 1")
    void deberiaArmarLaLlaveDeLaPrimeraCuenta() {
        // Arrange
        when(accountRepository.findKeysByPrefix("@MA123")).thenReturn(List.of());

        // Act
        String key = generator.build("Maria Fernanda", "1032456123");

        // Assert
        assertThat(key).isEqualTo("@MA1231").matches(DB_KEY_FORMAT);
        verify(accountRepository).findKeysByPrefix("@MA123");
    }

    @Test
    @DisplayName("HU10 CA01 - Las cuentas siguientes del mismo cliente toman los consecutivos 2 y 3")
    void deberiaUsarElSiguienteConsecutivo() {
        // Arrange
        when(accountRepository.findKeysByPrefix("@MA123"))
                .thenReturn(List.of("@MA1231"), List.of("@MA1231", "@MA1232"));

        // Act
        String second = generator.build("Maria", "123");
        String third = generator.build("Maria", "123");

        // Assert
        assertThat(second).isEqualTo("@MA1232");
        assertThat(third).isEqualTo("@MA1233");
    }

    @Test
    @DisplayName("HU10 CA06 - Otro cliente con el mismo prefijo no duplica la llave: recibe el siguiente libre")
    void noDeberiaDuplicarLaLlaveDeOtroCliente() {
        // Arrange
        when(accountRepository.findKeysByPrefix("@MA123")).thenReturn(List.of("@MA1231"));

        // Act
        String key = generator.build("Marta", "99123");

        // Assert
        assertThat(key).isEqualTo("@MA1232");
    }

    @Test
    @DisplayName("HU10 CA06 - Reutiliza el primer consecutivo libre, aunque haya uno mayor ocupado")
    void deberiaUsarElPrimerConsecutivoLibre() {
        // Arrange
        when(accountRepository.findKeysByPrefix("@JU456")).thenReturn(List.of("@JU4561", "@JU4563"));

        // Act
        String key = generator.build("Juan", "456");

        // Assert
        assertThat(key).isEqualTo("@JU4562");
    }

    @Test
    @DisplayName("HU10 CA06 - Una llave con consecutivo de dos cifras no se confunde con la de una cifra")
    void noDeberiaConfundirConsecutivosDeVariasCifras() {
        // Arrange
        List<String> taken = List.of("@MA1231", "@MA1232", "@MA1233", "@MA1234", "@MA1235",
                "@MA1236", "@MA1237", "@MA1238", "@MA1239", "@MA12310");

        when(accountRepository.findKeysByPrefix("@MA123")).thenReturn(taken);

        // Act
        String key = generator.build("Maria", "123");

        // Assert
        assertThat(key).isEqualTo("@MA12311").matches(DB_KEY_FORMAT);
    }

    @ParameterizedTest(name = "nombre \"{0}\", documento \"{1}\" -> {2}")
    @CsvSource({
            "'María José',  1032456123, @MA123",
            "'  ángel  Luis ', 45678,  @AN678",
            "'Ñusta',       12345,      @NU345",
            "'J',           12345,      @JX345",
            "'O''Neil',     12345,      @ON345",
            "'Ana',         45,         @AN045",
            "'Ana',         'CE-12.3',  @AN123",
            "'Ana',         'X9',       @AN009",
            "'',            12345,      @XX345"
    })
    @DisplayName("HU10 CA01 - Normaliza nombre y documento: mayúsculas, sin tildes ni símbolos y con relleno")
    void deberiaNormalizarElNombreYElDocumento(String firstNames, String documentNumber, String expectedPrefix) {
        // Arrange
        when(accountRepository.findKeysByPrefix(anyString())).thenReturn(List.of());

        // Act
        String key = generator.build(firstNames, documentNumber);

        // Assert
        assertThat(key).isEqualTo(expectedPrefix + "1").matches(DB_KEY_FORMAT);
    }

    @Test
    @DisplayName("HU10 CA01 - Sin nombre ni documento la llave igual cumple el formato de la base")
    void deberiaCumplirElFormatoConDatosNulos() {
        // Arrange
        when(accountRepository.findKeysByPrefix("@XX000")).thenReturn(List.of());

        // Act
        String key = generator.build(null, null);

        // Assert
        assertThat(key).isEqualTo("@XX0001").matches(DB_KEY_FORMAT);
    }
}
