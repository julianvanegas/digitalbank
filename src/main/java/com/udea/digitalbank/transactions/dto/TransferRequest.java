package com.udea.digitalbank.transactions.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

/**
 * Solicitud para iniciar una transferencia. El ordenante no viaja en el cuerpo: se toma del token de autenticación.
 */
@Data
public class TransferRequest {

    /** Número de la cuenta de origen (12 dígitos); debe pertenecer al ordenante. */
    @Schema(example = OpenApiExamples.ACCOUNT_NUMBER)
    @NotBlank(message = "La cuenta de origen es obligatoria")
    @Pattern(regexp = "\\d{12}", message = "El número de cuenta debe tener 12 dígitos")
    private String senderAccountNumber;

    /** Número de la cuenta de destino (12 dígitos); puede ser propia o de un tercero. */
    @Schema(example = OpenApiExamples.RECEIVER_ACCOUNT_NUMBER)
    @NotBlank(message = "La cuenta de destino es obligatoria")
    @Pattern(regexp = "\\d{12}", message = "El número de cuenta debe tener 12 dígitos")
    private String receiverAccountNumber;

    /**
     * Monto a transferir: positivo y con máximo dos decimales. Se usa {@link BigDecimal} (decimal exacto) para evitar
     * errores de redondeo; los límites coinciden con la columna {@code NUMERIC(19,2)}.
     */
    @Schema(example = OpenApiExamples.AMOUNT)
    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
    @Digits(integer = 17, fraction = 2, message = "El monto admite como máximo 2 decimales")
    private BigDecimal amount;
}
