package com.udea.digitalbank.accounts.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Solo lo necesario para conocer el saldo: el resto de la cuenta está en AccountResponse.
 * El estado va al lado del saldo para que el cliente distinga un saldo disponible de uno que no puede mover.
 */
@Data
public class AccountBalanceResponse {
    private String accountNumber;
    private BigDecimal balance;
    private String status;
}
