package com.udea.digitalbank.accounts.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AccountResponse {
    private UUID id;
    private String accountNumber;
    private UUID customerId;
    private String status;
    private BigDecimal balance;
    private LocalDateTime createdAt;
}
