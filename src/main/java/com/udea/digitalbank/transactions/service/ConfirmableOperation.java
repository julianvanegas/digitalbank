package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.auth.api.CodeCheckResult;
import com.udea.digitalbank.shared.exception.accounts.AccountNotActiveException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.exception.accounts.InsufficientFundsException;
import com.udea.digitalbank.shared.exception.transactions.TransactionLimitExceededException;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.dto.TransactionResponse;

/**
 * Lo que cambia de un tipo de transacción en el segundo paso, cuando se confirma con el código de segundo factor: cómo
 * se mueve el dinero y qué textos se le muestran al ordenante. Todo lo demás (buscar la transacción, comprobar al
 * ordenante, verificar el código, rechazar al agotar los intentos o al vencer el código) es igual para todos y vive en
 * {@link TransactionConfirmationService}.
 *
 * <p>Para que un tipo nuevo se confirme por {@code POST /api/v1/transactions/{reference}/confirm} basta con un
 * componente que implemente esta interfaz: el servicio recibe todos y elige por el tipo de la transacción.
 */
interface ConfirmableOperation {

    /** Tipo de transacción que esta operación sabe completar. */
    TransactionTypeEnum type();

    /**
     * Revalida los topes (si le aplican al ordenante), mueve el dinero y deja la transacción {@code COMPLETED}. Si el
     * saldo, el estado de la cuenta o el consumo del día cambiaron desde el primer paso, lanza la causa; el servicio
     * rechaza la transacción y la relanza.
     *
     * @throws AccountNotFoundException          si una de las cuentas ya no existe
     * @throws AccountNotActiveException         si una de las cuentas ya no está activa
     * @throws InsufficientFundsException        si el saldo ya no alcanza
     * @throws TransactionLimitExceededException si un cliente ya superaría un tope
     */
    TransactionResponse complete(TransferOperation operation, Transaction transaction);

    /** Texto del código incorrecto con intentos disponibles: la transacción sigue pendiente. */
    String wrongCode(CodeCheckResult check);

    /** Texto del último intento fallido: la transacción se cancela. */
    String exhaustedAttempts();

    /** Texto del código vencido o inexistente: la transacción se cancela. */
    String expiredCode();
}
