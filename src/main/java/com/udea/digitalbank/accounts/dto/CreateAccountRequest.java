package com.udea.digitalbank.accounts.dto;

import com.udea.digitalbank.accounts.api.AccountTypeEnum;
import lombok.Data;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

/**
 * El cuerpo es opcional: un cliente abre su propia cuenta sin enviar nada y el titular se toma del
 * token. Solo un administrador que abre la cuenta en nombre de un cliente indica aquí su customerId.
 * El tipo también es opcional: sin él la cuenta se abre como SAVINGS.
 */
@Data
public class CreateAccountRequest {
    @Schema(example = OpenApiExamples.CUSTOMER_ID)
    private UUID customerId;

    @Schema(example = "SAVINGS")
    private AccountTypeEnum accountType;
}
