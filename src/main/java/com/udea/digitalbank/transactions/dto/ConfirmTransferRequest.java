package com.udea.digitalbank.transactions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ConfirmTransferRequest {

    // Un código mal formado se rechaza aquí y no gasta uno de los intentos del código vigente
    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos")
    private String code;
}
