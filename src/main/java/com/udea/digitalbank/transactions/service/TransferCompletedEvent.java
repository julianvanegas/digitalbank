package com.udea.digitalbank.transactions.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Se publica cuando una transferencia se completa. Lleva los datos que necesita el comprobante por correo, para que
 * quien lo procesa no tenga que consultarlos de nuevo. Se atiende después de confirmarse la transacción de base de
 * datos.
 *
 * @param userId                ordenante, destinatario del comprobante
 * @param reference             comprobante de la transferencia
 * @param senderAccountNumber   número de la cuenta de origen
 * @param receiverAccountNumber número de la cuenta de destino
 * @param amount                monto transferido
 */
public record TransferCompletedEvent(UUID userId, String reference, String senderAccountNumber,
                                     String receiverAccountNumber, BigDecimal amount) {
}
