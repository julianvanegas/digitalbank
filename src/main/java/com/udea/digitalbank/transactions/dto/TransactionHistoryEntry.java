package com.udea.digitalbank.transactions.dto;

import com.udea.digitalbank.shared.config.OpenApiExamples;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un movimiento en el historial de una cuenta, visto desde esa cuenta: lo que entra es positivo y lo que sale es
 * negativo. Es distinto del comprobante ({@link TransactionResponse}), que muestra la operación completa y con montos
 * siempre positivos.
 */
@Data
public class TransactionHistoryEntry {
    /** Comprobante de la operación. */
    @Schema(example = OpenApiExamples.REFERENCE)
    private String reference;
    /** Tipo de movimiento: {@code TRANSFER}, {@code DEPOSIT} o {@code WITHDRAWAL}. */
    @Schema(implementation = TransactionTypeEnum.class, example = "TRANSFER")
    private String type;
    /**
     * Monto con signo desde la cuenta consultada: positivo si el dinero entró (transferencia recibida o depósito) y
     * negativo si salió (transferencia enviada o retiro).
     */
    @Schema(example = OpenApiExamples.NEGATIVE_AMOUNT)
    private BigDecimal amount;
    /**
     * Número de la otra cuenta de la transferencia: el destino si el dinero salió, el origen si entró. Es {@code null}
     * en un depósito o un retiro, que no tienen otra cuenta.
     */
    @Schema(example = OpenApiExamples.RECEIVER_ACCOUNT_NUMBER)
    private String counterpartAccountNumber;
    /** Instante en que se registró el movimiento. */
    @Schema(example = OpenApiExamples.TIMESTAMP)
    private LocalDateTime createdAt;
}
