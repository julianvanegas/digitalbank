package com.udea.digitalbank.accounts.dto;

import com.udea.digitalbank.accounts.domain.AccountAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UnlockAccountRequest {

    /**
     * El motivo del desbloqueo es obligatorio, igual que el del bloqueo. {@code @NotBlank} rechaza también una cadena
     * de solo espacios, que es lo mismo que exige el CHECK de la tabla account_actions.
     */
    @NotBlank(message = "El motivo del desbloqueo es obligatorio")
    @Size(max = AccountAction.MAX_REASON_LENGTH)
    private String reason;
}
