package com.udea.digitalbank.accounts.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;
import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountTypeEnum;

@Data
public class AccountResponse {
    @Schema(example = OpenApiExamples.ACCOUNT_ID)
    private UUID id;
    @Schema(example = OpenApiExamples.ACCOUNT_KEY)
    private String accountKey;
    @Schema(example = OpenApiExamples.ACCOUNT_NUMBER)
    private String accountNumber;
    @Schema(example = OpenApiExamples.CUSTOMER_ID)
    private UUID customerId;
    @Schema(implementation = AccountTypeEnum.class, example = "SAVINGS")
    private String accountType;
    @Schema(implementation = AccountStatusEnum.class, example = "ACTIVE")
    private String status;
    @Schema(example = OpenApiExamples.BALANCE)
    private BigDecimal balance;
    @Schema(example = OpenApiExamples.TIMESTAMP)
    private LocalDateTime createdAt;
    // Solo tiene valor si la cuenta está CLOSED
    @Schema(example = OpenApiExamples.CLOSED_AT)
    private LocalDateTime closedAt;
}
