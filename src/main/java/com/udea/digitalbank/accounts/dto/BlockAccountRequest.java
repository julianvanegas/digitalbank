package com.udea.digitalbank.accounts.dto;

import com.udea.digitalbank.accounts.domain.AccountAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

@Data
public class BlockAccountRequest {

    /**
     * El motivo es obligatorio. {@code @NotBlank} rechaza también una cadena de solo espacios,
     * que es lo mismo que exige el CHECK de la tabla account_actions.
     */
    @Schema(example = OpenApiExamples.REASON)
    @NotBlank(message = "El motivo del bloqueo es obligatorio")
    @Size(max = AccountAction.MAX_REASON_LENGTH)
    private String reason;
}
