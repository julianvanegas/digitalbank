package com.udea.digitalbank.accounts.api;

import java.math.BigDecimal;
import java.util.UUID;

// Vista de solo lectura de una cuenta para otros módulos (transactions)
public record AccountView(UUID id, String accountNumber, UUID customerId,
                          String status, BigDecimal balance) {
}
