package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.domain.AccountActionType;

import java.util.UUID;

/**
 * Se publica al bloquear, desbloquear o cerrar una cuenta. Lleva los datos que necesita la notificación para no volver a
 * consultarlos, y se procesa después de que la transacción confirme.
 */
public record AccountActionEvent(UUID customerId, String accountNumber, AccountActionType action,
                                      String reason) {
}
