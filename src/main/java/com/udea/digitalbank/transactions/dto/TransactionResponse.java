package com.udea.digitalbank.transactions.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Comprobante de una transacción. La referencia es lo que el cliente conserva para consultarla después.
 */
@Data
public class TransactionResponse {
    private String reference;
    private String type;
    private String status;
    private BigDecimal amount;
    private String senderAccountNumber;
    private String receiverAccountNumber;
    private LocalDateTime createdAt;
    // Solo tiene valor mientras la transacción espera el código: pasado ese momento hay que empezar de nuevo
    private LocalDateTime expiresAt;
}
