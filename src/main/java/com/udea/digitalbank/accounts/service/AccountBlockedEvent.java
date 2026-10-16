package com.udea.digitalbank.accounts.service;

import java.util.UUID;

/**
 * Se publica al bloquear una cuenta. Lleva los datos que necesita la notificación para no volver a
 * consultarlos, y se procesa después de que la transacción del bloqueo confirme.
 */
public record AccountBlockedEvent(UUID customerId, String accountNumber, String reason) {
}
