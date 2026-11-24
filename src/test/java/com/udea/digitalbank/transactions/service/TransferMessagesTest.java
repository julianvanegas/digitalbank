package com.udea.digitalbank.transactions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas unitarias (AAA) de {@link TransferMessages}: la redacción de lo que ve el ordenante y el titular. */
@DisplayName("TransferMessages")
class TransferMessagesTest {

    @Test
    @DisplayName("HU21 CA01 - El detalle del código nombra el monto, la cuenta destino enmascarada y la referencia, nunca el número completo")
    void deberiaDescribirLaOperacionSinElNumeroCompleto() {
        String detail = TransferMessages.codeDetail("", new BigDecimal("120.50"), "200000000002", "TRF-1");

        assertThat(detail).startsWith("Vas a transferir 120.50").contains("0002", "TRF-1")
                .doesNotContain("200000000002");
    }

    @Test
    @DisplayName("Admin en nombre del cliente - El aviso del empleado antecede al detalle")
    void deberiaAnteponerElAvisoDelEmpleado() {
        String detail = TransferMessages.codeDetail(TransferMessages.EMPLOYEE_NOTICE, BigDecimal.TEN,
                "200000000002", "TRF-1");

        assertThat(detail).startsWith("Un empleado del banco").contains("Vas a transferir 10");
    }

    @ParameterizedTest
    @CsvSource({"1,Te queda 1 intento", "2,Te quedan 2 intentos"})
    @DisplayName("HU21 CA02 - Los intentos restantes van en singular o plural")
    void deberiaConjugarLosIntentos(int attempts, String expected) {
        assertThat(TransferMessages.remainingAttempts(attempts)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"INACTIVE,está inactiva", "BLOCKED,está bloqueada", "CLOSED,está cerrada",
            "OTHER,su estado es OTHER"})
    @DisplayName("HU20 CA04 - Un estado de cuenta se describe en palabras")
    void deberiaDescribirElEstadoDeLaCuenta(String status, String expected) {
        assertThat(TransferMessages.accountStatus(status)).isEqualTo(expected);
    }
}
