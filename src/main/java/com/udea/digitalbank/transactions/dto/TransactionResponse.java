package com.udea.digitalbank.transactions.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;

/**
 * Comprobante de una transacción. La referencia es el dato que el ordenante conserva para consultarla después.
 */
@Data
public class TransactionResponse {
    /** Comprobante de la operación; identifica la transacción ante el ordenante. */
    @Schema(example = OpenApiExamples.REFERENCE)
    private String reference;
    /** Tipo de movimiento, por ejemplo {@code TRANSFER}. */
    @Schema(implementation = TransactionTypeEnum.class, example = "TRANSFER")
    private String type;
    /** Estado de la transacción, por ejemplo {@code PENDING} o {@code COMPLETED}. */
    @Schema(implementation = TransactionStatusEnum.class, example = "PENDING")
    private String status;
    /** Monto de la operación, siempre positivo. */
    @Schema(example = OpenApiExamples.AMOUNT)
    private BigDecimal amount;
    /** Número de la cuenta de origen; es {@code null} en un depósito. */
    @Schema(example = OpenApiExamples.ACCOUNT_NUMBER)
    private String senderAccountNumber;
    /** Número de la cuenta de destino; es {@code null} en un retiro. */
    @Schema(example = OpenApiExamples.RECEIVER_ACCOUNT_NUMBER)
    private String receiverAccountNumber;
    /** Instante de creación de la transacción. */
    @Schema(example = OpenApiExamples.TIMESTAMP)
    private LocalDateTime createdAt;
    /**
     * Instante en que vence el código de verificación. Solo se informa en la respuesta que inicia la transferencia
     * (estado {@code PENDING}); en las demás es {@code null}. Pasado ese instante se debe iniciar el proceso de nuevo.
     */
    @Schema(example = OpenApiExamples.EXPIRES_AT)
    private LocalDateTime expiresAt;
}
