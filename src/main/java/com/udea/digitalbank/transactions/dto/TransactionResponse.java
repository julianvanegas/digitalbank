package com.udea.digitalbank.transactions.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Comprobante de una transacción. La referencia es el dato que el ordenante conserva para consultarla después.
 */
@Data
public class TransactionResponse {
    /** Comprobante de la operación; identifica la transacción ante el ordenante. */
    private String reference;
    /** Tipo de movimiento, por ejemplo {@code TRANSFER}. */
    private String type;
    /** Estado de la transacción, por ejemplo {@code PENDING} o {@code COMPLETED}. */
    private String status;
    /** Monto de la operación, siempre positivo. */
    private BigDecimal amount;
    /** Número de la cuenta de origen; es {@code null} en un depósito. */
    private String senderAccountNumber;
    /** Número de la cuenta de destino; es {@code null} en un retiro. */
    private String receiverAccountNumber;
    /** Instante de creación de la transacción. */
    private LocalDateTime createdAt;
    /**
     * Instante en que vence el código de verificación. Solo se informa en la respuesta que inicia la transferencia
     * (estado {@code PENDING}); en las demás es {@code null}. Pasado ese instante se debe iniciar el proceso de nuevo.
     */
    private LocalDateTime expiresAt;
}
