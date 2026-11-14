package com.udea.digitalbank.transactions.dto;

import com.udea.digitalbank.shared.config.OpenApiExamples;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Solicitud de un depósito (consignación) a una cuenta. El administrador que lo registra no viaja en el cuerpo: se
 * toma del token de autenticación.
 */
@Data
public class DepositRequest {

    /** Número de la cuenta de destino (12 dígitos). */
    @Schema(example = OpenApiExamples.RECEIVER_ACCOUNT_NUMBER)
    @NotBlank(message = "La cuenta de destino es obligatoria")
    @Pattern(regexp = "\\d{12}", message = "El número de cuenta debe tener 12 dígitos")
    private String receiverAccountNumber;

    /**
     * Monto a consignar: mayor que cero y con máximo dos decimales. Los límites coinciden con la columna
     * {@code NUMERIC(19,2)}.
     */
    @Schema(example = OpenApiExamples.AMOUNT)
    @NotNull(message = "Debe ingresar un valor válido")
    @DecimalMin(value = "0.01", message = "Debe ingresar un valor válido: el monto debe ser mayor que cero")
    @Digits(integer = 17, fraction = 2, message = "El monto admite como máximo 2 decimales")
    private BigDecimal amount;
}
