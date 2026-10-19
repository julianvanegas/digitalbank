package com.udea.digitalbank.transactions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

/** Cuerpo de la confirmación de una transferencia: el código de segundo factor que recibe el ordenante. */
@Data
public class ConfirmTransferRequest {

    /**
     * Código de 6 dígitos enviado por correo. Un código con formato inválido se rechaza en la validación y no consume
     * uno de los intentos del código vigente.
     */
    @Schema(example = OpenApiExamples.CODE)
    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos")
    private String code;
}
