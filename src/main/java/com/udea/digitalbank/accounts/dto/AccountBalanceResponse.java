package com.udea.digitalbank.accounts.dto;

import lombok.Data;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;
import com.udea.digitalbank.accounts.api.AccountStatusEnum;

/**
 * Solo lo necesario para conocer el saldo: el resto de la cuenta está en AccountResponse.
 * El estado va al lado del saldo para que el cliente distinga un saldo disponible de uno que no puede mover.
 */
@Data
public class AccountBalanceResponse {
    @Schema(example = OpenApiExamples.ACCOUNT_NUMBER)
    private String accountNumber;
    @Schema(example = OpenApiExamples.BALANCE)
    private BigDecimal balance;
    @Schema(implementation = AccountStatusEnum.class, example = "ACTIVE")
    private String status;
}
