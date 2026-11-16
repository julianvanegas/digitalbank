package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.auth.api.CodeCheckResult;
import com.udea.digitalbank.auth.api.VerificationApi;
import com.udea.digitalbank.shared.exception.BusinessException;
import com.udea.digitalbank.shared.exception.InvalidRequestException;
import com.udea.digitalbank.shared.exception.TooManyRequestsException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotActiveException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.exception.accounts.InsufficientFundsException;
import com.udea.digitalbank.shared.exception.transactions.InvalidTransactionCodeException;
import com.udea.digitalbank.shared.exception.transactions.TransactionCodeExpiredException;
import com.udea.digitalbank.shared.exception.transactions.TransactionLimitExceededException;
import com.udea.digitalbank.shared.exception.transactions.TransactionNotFoundException;
import com.udea.digitalbank.shared.exception.transactions.TransactionNotPendingException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Segundo paso de las operaciones que se autorizan con un código de segundo factor (transferencias y retiros):
 * verifica el código y, solo entonces, mueve el dinero. El primer paso es {@link TransferInitiationService} o
 * {@link WithdrawalInitiationService}. Lo expone {@code POST /api/v1/transactions/{reference}/confirm}, para todos los
 * tipos: la referencia identifica la transacción y su tipo decide cómo se completa.
 *
 * <p><b>Un flujo, una pieza por tipo.</b> Buscar la transacción, comprobar al ordenante, verificar el código y rechazar
 * al agotar los intentos o al vencer el código es común. Lo propio de cada tipo (mover el dinero y los textos) está en
 * su {@link ConfirmableOperation}: {@link TransferConfirmable} y {@link WithdrawalConfirmable}. Una referencia cuyo
 * tipo no tiene operación (un depósito, que se hace en un paso) se rechaza antes de verificar el código.
 *
 * <p><b>Qué se registra.</b> Pasan a {@code REJECTED} las transacciones que se cancelan en este paso: código agotado o
 * vencido, o inválida porque el saldo, el estado de las cuentas o los topes han cambiado desde el primer paso.
 *
 * <p><b>Concurrencia.</b> El saldo, el estado de las cuentas y los topes se vuelven a validar al confirmar, con las
 * filas bloqueadas, porque pueden cambiar entre los dos pasos.
 *
 * <p><b>Cliente y administrador.</b> Confirma quien ordenó la operación (ADR-0005). El código es del titular de la
 * cuenta de origen: si confirma un administrador, ingresa el que recibió el titular. Los topes solo se revalidan para
 * clientes.
 */
@Service
public class TransactionConfirmationService {

    private final TransactionRepository transactionRepository;
    private final TransactionResponses responses;
    private final TransferParties parties;
    private final PendingTransfers pendingTransfers;
    private final VerificationApi verificationApi;
    private final List<ConfirmableOperation> operations;

    TransactionConfirmationService(TransactionRepository transactionRepository,
                                   TransactionResponses responses,
                                   TransferParties parties,
                                   PendingTransfers pendingTransfers,
                                   VerificationApi verificationApi,
                                   List<ConfirmableOperation> operations) {
        this.transactionRepository = transactionRepository;
        this.responses = responses;
        this.parties = parties;
        this.pendingTransfers = pendingTransfers;
        this.verificationApi = verificationApi;
        this.operations = operations;
    }

    /**
     * Verifica el código de segundo factor y, si es correcto, mueve el dinero y completa la transacción.
     *
     * <p>Un código incorrecto deja la transacción {@code PENDING} con los intentos que quedan; al agotarlos, o si el
     * código ha vencido, se rechaza y se debe iniciar una nueva. Es idempotente: confirmar una transacción ya
     * {@code COMPLETED} devuelve el mismo comprobante sin mover dinero otra vez.
     *
     * <p><b>Transaccionalidad.</b> Usa {@code noRollbackFor = BusinessException.class} para que el rechazo de la
     * transacción y el contador de intentos fallidos se persistan aunque el método termine lanzando la excepción que
     * recibe el ordenante. Sin esa opción, la excepción revertiría la transacción de base de datos y nada de eso
     * quedaría guardado.
     *
     * @param actor     usuario autenticado; debe ser el ordenante de la transacción. Si es administrador, el código
     *                  que ingresa es el que recibió el titular de la cuenta de origen
     * @param reference comprobante de la transferencia o del retiro
     * @param code      código de 6 dígitos recibido por correo
     * @return el comprobante de la transacción ya completada
     * @throws TransactionNotFoundException      si la referencia no existe o pertenece a otro usuario
     * @throws InvalidRequestException           si la referencia es de un tipo que no se confirma con código (un
     *                                           depósito); se rechaza antes de verificar el código, que no se gasta
     * @throws TransactionNotPendingException    si la transacción está cancelada o revertida
     * @throws InvalidTransactionCodeException   si el código es incorrecto y quedan intentos
     * @throws TooManyRequestsException          si se han agotado los intentos; la transacción queda {@code REJECTED}
     * @throws TransactionCodeExpiredException   si el código ha vencido; la transacción queda {@code REJECTED}
     * @throws InsufficientFundsException        si al mover el dinero el saldo ya no alcanza; queda {@code REJECTED}
     * @throws TransactionLimitExceededException si un cliente ya superaría un tope; queda {@code REJECTED}
     * @throws AccountNotActiveException         si una de las cuentas ya no está activa; queda {@code REJECTED}
     * @throws AccountNotFoundException          si una de las cuentas ya no existe; queda {@code REJECTED}
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TransactionResponse confirm(Actor actor, String reference, String code) {
        Transaction transaction = transactionRepository.findByReferenceForUpdate(reference)
                .filter(t -> t.isOrderedBy(actor.userId()))
                .orElseThrow(() -> new TransactionNotFoundException("Transacción no encontrada: " + reference));

        // Antes que nada que dependa del tipo: el comprobante de una transacción completada y, en especial, la
        // verificación del código, que gastaría un intento del cliente por mandar una referencia que no se confirma
        ConfirmableOperation operation = operations.stream()
                .filter(o -> transaction.getType().is(o.type()))
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("La referencia " + reference
                        + " no se confirma con código: aquí solo se confirman transferencias y retiros"));

        if (transaction.is(TransactionStatusEnum.COMPLETED)) {
            return responses.of(transaction);
        }
        transaction.requireConfirmable();

        TransferOperation who = parties.operationFor(actor, transaction.getSenderAccountId());
        CodeCheckResult check = verificationApi.verifyTransactionCode(who.codeOwnerId(), code);
        return switch (check.outcome()) {
            case WRONG -> throw new InvalidTransactionCodeException(operation.wrongCode(check));
            case EXHAUSTED -> {
                pendingTransfers.reject(transaction);
                throw new TooManyRequestsException(operation.exhaustedAttempts(), 0);
            }
            case EXPIRED -> {
                pendingTransfers.reject(transaction);
                throw new TransactionCodeExpiredException(operation.expiredCode());
            }
            case VALID -> completeOrReject(operation, who, transaction);
        };
    }

    /**
     * Completa la transacción con la operación de su tipo. Si el saldo, el estado de las cuentas o el consumo del día
     * cambiaron desde el primer paso, la rechaza y relanza la causa para que el ordenante la conozca.
     */
    private TransactionResponse completeOrReject(ConfirmableOperation operation, TransferOperation transferOperation,
                                                 Transaction transaction) {
        try {
            return operation.complete(transferOperation, transaction);
        } catch (AccountNotFoundException | AccountNotActiveException | InsufficientFundsException
                 | TransactionLimitExceededException e) {
            pendingTransfers.reject(transaction);
            throw e;
        }
    }
}
