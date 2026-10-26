package com.udea.digitalbank.transactions.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Validación de los cuerpos de transacciones: lo que se rechaza antes de llegar al servicio. */
@DisplayName("Validación de las solicitudes de transacciones")
class TransactionRequestsValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static TransferRequest transfer(String from, String to, BigDecimal amount) {
        TransferRequest request = new TransferRequest();
        request.setSenderAccountNumber(from);
        request.setReceiverAccountNumber(to);
        request.setAmount(amount);
        return request;
    }

    @Test
    @DisplayName("HU20 CA01 - Una transferencia con cuentas de 12 dígitos y un monto con dos decimales es válida")
    void deberiaAceptarUnaSolicitudValida() {
        assertThat(validator.validate(transfer("100000000001", "200000000002", new BigDecimal("0.01")))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.00", "0.001", "10.123"})
    @NullSource
    @DisplayName("HU20 complementario - El monto debe ser positivo, con máximo dos decimales y obligatorio")
    void deberiaRechazarMontosInvalidos(String amount) {
        BigDecimal value = amount == null ? null : new BigDecimal(amount);

        assertThat(validator.validate(transfer("100000000001", "200000000002", value))).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "12345", "1234567890123", "10000000000a"})
    @NullSource
    @DisplayName("HU20 CA04 - Los números de cuenta deben tener exactamente 12 dígitos")
    void deberiaRechazarNumerosDeCuentaInvalidos(String number) {
        assertThat(validator.validate(transfer(number, "200000000002", BigDecimal.TEN))).isNotEmpty();
        assertThat(validator.validate(transfer("100000000001", number, BigDecimal.TEN))).isNotEmpty();
    }

    @Test
    @DisplayName("HU21 CA02 - El código de confirmación son 6 dígitos")
    void deberiaValidarElFormatoDelCodigo() {
        ConfirmTransferRequest valid = new ConfirmTransferRequest();
        valid.setCode("012345");
        assertThat(validator.validate(valid)).isEmpty();

        for (String bad : new String[]{null, "", "12345", "1234567", "12345a"}) {
            ConfirmTransferRequest invalid = new ConfirmTransferRequest();
            invalid.setCode(bad);
            assertThat(validator.validate(invalid)).as("código '%s'", bad).isNotEmpty();
        }
    }
}
