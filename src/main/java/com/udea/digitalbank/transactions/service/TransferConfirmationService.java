package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.auth.api.CodeCheckResult;
import com.udea.digitalbank.auth.api.VerificationApi;
import com.udea.digitalbank.shared.exception.BusinessException;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Segundo paso de la transferencia entre cuentas del banco: verifica el código de segundo factor y, solo entonces,
 * mueve el dinero. El primer paso es {@link TransferInitiationService}.
 *
 * <p><b>Qué se registra.</b> Pasan a {@code REJECTED} las transacciones que se cancelan en este paso: código agotado o
 * vencido, o inválida porque el saldo, el estado de las cuentas o los topes han cambiado desde el primer paso.
 *
 * <p><b>Concurrencia.</b> El saldo, el estado de las cuentas y los topes se vuelven a validar al confirmar, con las
 * filas bloqueadas, porque pueden cambiar entre los dos pasos.
 *
 * <p><b>Cliente y administrador.</b> Confirma quien ordenó la transferencia. El código es del titular de la cuenta de
 * origen: si confirma un administrador, ingresa el que recibió el titular. Los topes solo se revalidan para clientes.
 */
@Service
public class TransferConfirmationService {

    private final TransactionRepository transactionRepository;
    private final TransactionCatalog catalog;
    private final TransactionResponses responses;
    private final TransferParties parties;
    private final PendingTransfers pendingTransfers;
    private final AccountApi accountApi;
    private final VerificationApi verificationApi;
    private final ApplicationEventPublisher eventPublisher;

    public TransferConfirmationService(TransactionRepository transactionRepository,
                                       TransactionCatalog catalog,
                                       TransactionResponses responses,
                                       TransferParties parties,
                                       PendingTransfers pendingTransfers,
                                       AccountApi accountApi,
                                       VerificationApi verificationApi,
                                       ApplicationEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.catalog = catalog;
        this.responses = responses;
        this.parties = parties;
        this.pendingTransfers = pendingTransfers;
        this.accountApi = accountApi;
        this.verificationApi = verificationApi;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Verifica el código de segundo factor y, si es correcto, mueve el dinero y completa la transferencia.
     *
     * <p>Un código incorrecto deja la transferencia {@code PENDING} con los intentos que quedan; al agotarlos, o si el
     * código ha vencido, se rechaza y se debe iniciar una nueva. Es idempotente: confirmar una transferencia ya
     * {@code COMPLETED} devuelve el mismo comprobante sin mover dinero otra vez.
     *
     * <p><b>Transaccionalidad.</b> Usa {@code noRollbackFor = BusinessException.class} para que el rechazo de la
     * transferencia y el contador de intentos fallidos se persistan aunque el método termine lanzando la excepción que
     * recibe el ordenante. Sin esa opción, la excepción revertiría la transacción de base de datos y nada de eso
     * quedaría guardado.
     *
     * @param actor     usuario autenticado; debe ser el ordenante de la transferencia. Si es administrador, el código
     *                  que ingresa es el que recibió el titular de la cuenta de origen
     * @param reference comprobante de la transferencia
     * @param code      código de 6 dígitos recibido por correo
     * @return el comprobante de la transferencia ya completada
     * @throws TransactionNotFoundException      si la referencia no existe o pertenece a otro usuario
     * @throws TransactionNotPendingException    si la transferencia está cancelada o revertida
     * @throws InvalidTransactionCodeException   si el código es incorrecto y quedan intentos
     * @throws TooManyRequestsException          si se han agotado los intentos; la transferencia queda {@code REJECTED}
     * @throws TransactionCodeExpiredException   si el código ha vencido; la transferencia queda {@code REJECTED}
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

        if (transaction.is(TransactionStatusEnum.COMPLETED)) {
            return responses.of(transaction);
        }
        transaction.requireConfirmable();

        TransferOperation operation = parties.operationFor(actor, transaction.getSenderAccountId());
        CodeCheckResult check = verificationApi.verifyTransactionCode(operation.codeOwnerId(), code);
        return switch (check.outcome()) {
            case WRONG -> throw wrongCode(check);
            case EXHAUSTED -> throw exhaustedAttempts(transaction);
            case EXPIRED -> throw expiredCode(transaction);
            case VALID -> complete(operation, transaction);
        };
    }

    /**
     * Revalida los topes (si le aplican al ordenante) y mueve el dinero con las dos cuentas bloqueadas, revalidándolas.
     * Si el saldo, el estado o el consumo del día cambiaron desde el primer paso, rechaza la transferencia y relanza
     * la causa para que el ordenante la conozca. El comprobante por correo va al titular de la cuenta de origen.
     */
    private TransactionResponse complete(TransferOperation operation, Transaction transaction) {
        try {
            parties.enforceLimits(operation, transaction.getSenderAccountId(), transaction.getAmount());
            accountApi.applyTransfer(transaction.getSenderAccountId(), transaction.getReceiverAccountId(),
                    transaction.getAmount());
        } catch (AccountNotFoundException | AccountNotActiveException | InsufficientFundsException
                 | TransactionLimitExceededException e) {
            pendingTransfers.reject(transaction);
            throw e;
        }
        transaction.complete(catalog.status(TransactionStatusEnum.COMPLETED));
        transactionRepository.save(transaction);

        TransactionResponse response = responses.of(transaction);
        eventPublisher.publishEvent(new TransferCompletedEvent(operation.codeOwnerId(),
                transaction.getReference(), response.getSenderAccountNumber(), response.getReceiverAccountNumber(),
                transaction.getAmount()));
        return response;
    }

    // Código incorrecto con intentos disponibles: la transferencia sigue pendiente
    private static InvalidTransactionCodeException wrongCode(CodeCheckResult check) {
        return new InvalidTransactionCodeException(
                "Código incorrecto. " + TransferMessages.remainingAttempts(check.remainingAttempts()));
    }

    // Último intento fallido: se cancela la transferencia
    private TooManyRequestsException exhaustedAttempts(Transaction transaction) {
        pendingTransfers.reject(transaction);
        return new TooManyRequestsException(
                "Superaste el número de intentos: la transferencia fue cancelada. Inicia una nueva", 0);
    }

    // Código vencido o inexistente: se cancela la transferencia
    private TransactionCodeExpiredException expiredCode(Transaction transaction) {
        pendingTransfers.reject(transaction);
        return new TransactionCodeExpiredException(
                "El código expiró: la transferencia fue cancelada. Inicia el proceso de nuevo");
    }
}
