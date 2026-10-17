package com.udea.digitalbank.accounts.dto;

import com.udea.digitalbank.accounts.domain.AccountAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BlockAccountRequest {

    /**
     * El motivo es obligatorio. {@code @NotBlank} rechaza también una cadena de solo espacios,
     * que es lo mismo que exige el CHECK de la tabla account_actions.
     */
    @NotBlank(message = "El motivo del bloqueo es obligatorio")
    @Size(max = AccountAction.MAX_REASON_LENGTH)
    private String reason;
}
