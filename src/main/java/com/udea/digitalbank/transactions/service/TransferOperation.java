package com.udea.digitalbank.transactions.service;

import java.util.UUID;

/**
 * Lo que cambia en una transferencia según quién la ordena, resuelto una sola vez por
 * {@link TransferParties#operationFor}.
 *
 * @param codeOwnerId      usuario al que pertenece el código de segundo factor: el titular de la cuenta de origen
 * @param enforceLimits    si se aplican los topes: a los clientes sí, a los administradores no
 * @param codeDetailPrefix texto que antecede al detalle del correo del código; vacío si la ordena el titular
 */
record TransferOperation(UUID codeOwnerId, boolean enforceLimits, String codeDetailPrefix) {
}
