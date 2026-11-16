package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountView;
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
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Segundo paso del retiro (HU19): verifica el código de segundo factor y, solo entonces, debita la cuenta. El primer
 * paso es {@link WithdrawalInitiationService}. Sigue el flujo de {@link TransferConfirmationService}, para un movimiento
 * de un solo lado. Lo expone {@code WithdrawalController}.
 *
 * <p><b>Qué se registra.</b> Pasan a {@code REJECTED} los retiros que se cancelan en este paso: código agotado o
 * vencido, o inválido porque el saldo, el estado de la cuenta o los topes han cambiado desde el primer paso.
 *
 * <p><b>Concurrencia.</b> El saldo, el estado de la cuenta y los topes se vuelven a validar al confirmar, con la fila
 * de la cuenta bloqueada ({@code applyWithdrawal}), porque pueden cambiar entre los dos pasos.
 *
 * <p><b>Cliente y administrador.</b> Como en la transferencia (ADR-0005): confirma quien ordenó el retiro, y el código
 * es del titular de la cuenta. Si ordenó un administrador, ingresa el código que recibió el titular; el titular no
 * puede confirmar un retiro que no ordenó. Los topes solo se revalidan para clientes.
 */
@Service
public class WithdrawalConfirmationService {

    private final TransactionRepository transactionRepository;
    private final TransactionCatalog catalog;
    private final TransactionResponses responses;
    private final TransferParties parties;
    private final PendingTransfers pendingTransfers;
    private final TransactionLimitService limitService;
    private final AccountApi accountApi;
    private final VerificationApi verificationApi;

    public WithdrawalConfirmationService(TransactionRepository transactionRepository,
                                         TransactionCatalog catalog,
                                         TransactionResponses responses,
                                         TransferParties parties,
                                         PendingTransfers pendingTransfers,
                                         TransactionLimitService limitService,
                                         AccountApi accountApi,
                                         VerificationApi verificationApi) {
        this.transactionRepository = transactionRepository;
        this.catalog = catalog;
        this.responses = responses;
        this.parties = parties;
        this.pendingTransfers = pendingTransfers;
        this.limitService = limitService;
        this.accountApi = accountApi;
        this.verificationApi = verificationApi;
    }

    /**
     * Verifica el código de segundo factor y, si es correcto, debita la cuenta y completa el retiro (CA01).
     *
     * <p>Un código incorrecto deja el retiro {@code PENDING} con los intentos que quedan y el intento queda registrado
     * en {@code auth} (CA04); al agotarlos, o si el código ha vencido, se rechaza y se debe iniciar uno nuevo. Es
     * idempotente: confirmar un retiro ya {@code COMPLETED} devuelve el mismo comprobante sin debitar otra vez.
     *
     * <p><b>Transaccionalidad.</b> Usa {@code noRollbackFor = BusinessException.class} para que el rechazo del retiro y
     * el contador de intentos fallidos se persistan aunque el método termine lanzando la excepción que recibe el
     * ordenante. El {@code noRollbackFor} de {@code applyWithdrawal} solo evita que ese método marque la transacción;
     * sin el de aquí, al relanzar la excepción se revertiría la transacción completa y se perdería el registro del retiro rechazado.
     *
     * @param actor     usuario autenticado; debe ser el ordenante del retiro. Si es administrador, el código que
     *                  ingresa es el que recibió el titular de la cuenta
     * @param reference comprobante del retiro
     * @param code      código de 6 dígitos recibido por correo
     * @return el comprobante del retiro ya completado
     * @throws TransactionNotFoundException      si la referencia no existe o pertenece a otro usuario
     * @throws InvalidRequestException           si la referencia es de otro tipo de transacción; se rechaza antes de
     *                                           verificar el código, que no se gasta
     * @throws TransactionNotPendingException    si el retiro está cancelado o revertido
     * @throws InvalidTransactionCodeException   si el código es incorrecto y quedan intentos (CA04)
     * @throws TooManyRequestsException          si se han agotado los intentos; el retiro queda {@code REJECTED} (CA04)
     * @throws TransactionCodeExpiredException   si el código ha vencido; el retiro queda {@code REJECTED}
     * @throws InsufficientFundsException        si al debitar el saldo ya no alcanza (CA02); queda {@code REJECTED}
     * @throws TransactionLimitExceededException si un cliente ya superaría un tope (CA03); queda {@code REJECTED}
     * @throws AccountNotActiveException         si la cuenta ya no está activa; queda {@code REJECTED}
     * @throws AccountNotFoundException          si la cuenta ya no existe; queda {@code REJECTED}
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TransactionResponse confirm(Actor actor, String reference, String code) {
        Transaction transaction = transactionRepository.findByReferenceForUpdate(reference)
                .filter(t -> t.isOrderedBy(actor.userId()))
                .orElseThrow(() -> new TransactionNotFoundException("Transacción no encontrada: " + reference));

        // Simétrica de la guarda de la confirmación de transferencias: antes del comprobante y del código
        if (!transaction.getType().is(TransactionTypeEnum.WITHDRAWAL)) {
            throw new InvalidRequestException(WithdrawalMessages.notAWithdrawal(reference));
        }
        if (transaction.is(TransactionStatusEnum.COMPLETED)) {
            return responses.of(transaction);
        }
        transaction.requireConfirmable();

        TransferOperation operation = parties.operationFor(actor, transaction.getSenderAccountId());
        CodeCheckResult check = verificationApi.verifyTransactionCode(operation.codeOwnerId(), code);
        return switch (check.outcome()) {
            case WRONG -> throw new InvalidTransactionCodeException(
                    WithdrawalMessages.wrongCode(check.remainingAttempts()));
            case EXHAUSTED -> throw exhaustedAttempts(transaction);
            case EXPIRED -> throw expiredCode(transaction);
            case VALID -> complete(operation, transaction);
        };
    }

    /**
     * Revalida los topes (si le aplican al ordenante) y debita la cuenta con su fila bloqueada, revalidándola. Si el
     * saldo, el estado o el consumo del día cambiaron desde el primer paso, rechaza el retiro y relanza la causa para
     * que el ordenante la conozca.
     */
    private TransactionResponse complete(TransferOperation operation, Transaction transaction) {
        AccountView debited;
        try {
            if (operation.enforceLimits()) {
                limitService.requireWithinLimits(accountApi.getAccount(transaction.getSenderAccountId()),
                        TransactionTypeEnum.WITHDRAWAL, transaction.getAmount());
            }
            debited = accountApi.applyWithdrawal(transaction.getSenderAccountId(), transaction.getAmount());
        } catch (AccountNotFoundException | AccountNotActiveException | InsufficientFundsException
                 | TransactionLimitExceededException e) {
            pendingTransfers.reject(transaction);
            throw e;
        }
        transaction.complete(catalog.status(TransactionStatusEnum.COMPLETED));
        transactionRepository.save(transaction);
        return responses.of(transaction, debited.accountNumber(), null);
    }

    // Último intento fallido: se cancela el retiro
    private TooManyRequestsException exhaustedAttempts(Transaction transaction) {
        pendingTransfers.reject(transaction);
        return new TooManyRequestsException(WithdrawalMessages.EXHAUSTED_ATTEMPTS, 0);
    }

    // Código vencido o inexistente: se cancela el retiro
    private TransactionCodeExpiredException expiredCode(Transaction transaction) {
        pendingTransfers.reject(transaction);
        return new TransactionCodeExpiredException(WithdrawalMessages.EXPIRED_CODE);
    }
}
