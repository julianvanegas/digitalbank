package com.udea.digitalbank.transactions.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

// Quién ordena la transferencia no viaja aquí: sale del token
@Data
public class TransferRequest {

    @NotBlank(message = "La cuenta de origen es obligatoria")
    @Pattern(regexp = "\\d{12}", message = "El número de cuenta debe tener 12 dígitos")
    private String senderAccountNumber;

    @NotBlank(message = "La cuenta de destino es obligatoria")
    @Pattern(regexp = "\\d{12}", message = "El número de cuenta debe tener 12 dígitos")
    private String receiverAccountNumber;

    // Decimal exacto con la escala de la columna (19,2): nunca punto flotante
    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
    @Digits(integer = 17, fraction = 2, message = "El monto admite como máximo 2 decimales")
    private BigDecimal amount;
}
