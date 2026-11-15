package com.udea.digitalbank.transactions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

/**
 * Cuerpo de la confirmación de un retiro (HU19): el código de segundo factor que recibe el titular de la cuenta. Tiene
 * la misma validación que {@link ConfirmTransferRequest}; es otra clase para que la documentación hable de retiros.
 */
@Data
public class ConfirmWithdrawalRequest {
    /**
     * Código de 6 dígitos enviado por correo al titular. Un código con formato inválido se rechaza en la validación y
     * no consume uno de los intentos del código vigente.
     */
    @Schema(example = OpenApiExamples.CODE)
    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos")
    private String code;
}
