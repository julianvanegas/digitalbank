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
 * Solicitud para iniciar un retiro (HU19). El ordenante no viaja en el cuerpo: se toma del token de autenticación.
 * Las validaciones del monto son las mismas que las de {@link TransferRequest} y {@link DepositRequest}.
 */
@Data
public class WithdrawalRequest {
    /** Número de la cuenta de la que sale el dinero (12 dígitos); para un cliente, debe ser suya. */
    @Schema(example = OpenApiExamples.ACCOUNT_NUMBER)
    @NotBlank(message = "La cuenta es obligatoria")
    @Pattern(regexp = "\\d{12}", message = "El número de cuenta debe tener 12 dígitos")
    private String accountNumber;
    /**
     * Monto a retirar: positivo y con máximo dos decimales. Los límites coinciden con la columna {@code NUMERIC(19,2)}.
     * Esta validación es la que garantiza que el servicio pueda fijar la escala en 2 sin redondear.
     */
    @Schema(example = OpenApiExamples.AMOUNT)
    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
    @Digits(integer = 17, fraction = 2, message = "El monto admite como máximo 2 decimales")
    private BigDecimal amount;
}
