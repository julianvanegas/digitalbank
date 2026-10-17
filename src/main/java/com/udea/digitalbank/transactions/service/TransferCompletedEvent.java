package com.udea.digitalbank.transactions.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Se publica al completarse una transferencia. Lleva lo que necesita el comprobante por correo para no volver a
 * consultarlo, y se procesa después de que la transacción confirme.
 */
public record TransferCompletedEvent(UUID userId, String reference, String senderAccountNumber,
                                     String receiverAccountNumber, BigDecimal amount) {
}
