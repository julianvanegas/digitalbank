package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.auth.api.CodeCheckResult;
import com.udea.digitalbank.auth.api.VerificationApi;
import com.udea.digitalbank.shared.exception.BusinessException;
import com.udea.digitalbank.shared.exception.TooManyRequestsException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotActiveException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.exception.accounts.InsufficientFundsException;
import com.udea.digitalbank.shared.exception.transactions.InvalidReceiverAccountException;
import com.udea.digitalbank.shared.exception.transactions.InvalidTransactionCodeException;
import com.udea.digitalbank.shared.exception.transactions.SameAccountTransferException;
import com.udea.digitalbank.shared.exception.transactions.TransactionCodeExpiredException;
import com.udea.digitalbank.shared.exception.transactions.TransactionNotFoundException;
import com.udea.digitalbank.shared.exception.transactions.TransactionNotPendingException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.shared.utils.AccountNumbers;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.dto.TransferRequest;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transferencias entre cuentas del banco, en dos pasos. {@link #initiate} valida la operación y la deja PENDING
 * mientras el segundo factor la autoriza; {@link #confirm} verifica el código y, solo entonces, mueve el dinero.
 * <p>
 * Las validaciones del primer paso fallan rápido y no dejan fila: una transferencia imposible no es una transacción.
 * Quedan REJECTED solo las que se cancelan después de haber nacido: código agotado o vencido, reemplazada por otra o
 * con saldo o cuentas distintas al confirmar. El saldo se vuelve a validar al confirmar, con las filas bloqueadas,
 * porque pudo cambiar entre los dos pasos.
 */
@Service
public class TransferService {

    private final TransactionRepository transactionRepository;
    private final TransactionCatalog catalog;
    private final TransactionResponses responses;
    private final TransactionReferenceGenerator referenceGenerator;
    private final AccountApi accountApi;
    private final VerificationApi verificationApi;
    private final ApplicationEventPublisher eventPublisher;

    public TransferService(TransactionRepository transactionRepository,
                           TransactionCatalog catalog,
                           TransactionResponses responses,
                           TransactionReferenceGenerator referenceGenerator,
                           AccountApi accountApi,
                           VerificationApi verificationApi,
                           ApplicationEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.catalog = catalog;
        this.responses = responses;
        this.referenceGenerator = referenceGenerator;
        this.accountApi = accountApi;
        this.verificationApi = verificationApi;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Primer paso: valida la transferencia y la deja PENDING, con un código de verificación enviado al correo
     * del cliente. Una transferencia pendiente anterior del mismo cliente se cancela: el código nuevo reemplaza
     * al anterior, así que solo puede haber una esperando.
     * <p>
     * Se valida, en este orden: que no sea la misma cuenta, que la de origen sea del cliente y esté activa,
     * que la de destino exista y esté activa y que el saldo alcance.
     */
    @Transactional
    public TransactionResponse initiate(Actor actor, TransferRequest request) {
        String senderNumber = request.getSenderAccountNumber();
        String receiverNumber = request.getReceiverAccountNumber();
        BigDecimal amount = request.getAmount().setScale(2);

        if (senderNumber.equals(receiverNumber)) {
            throw new SameAccountTransferException("No se pueden realizar transferencias a la misma cuenta");
        }

        // Una cuenta ajena responde como inexistente: no se confirma que el número existe
        AccountView sender = accountApi.requireOwnedAccount(actor.userId(), senderNumber);
        if (!sender.isActive()) {
            throw new AccountNotActiveException(
                    "La cuenta de origen no está activa: " + describe(sender.status()));
        }
        AccountView receiver = activeReceiver(receiverNumber);
        if (sender.balance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Fondos insuficientes en la cuenta de origen");
        }

        rejectPendingOf(actor.userId());

        // El id se asigna ya para derivar de él la referencia (el comprobante) antes de guardar
        UUID transactionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Transaction transaction = transactionRepository.save(Transaction.transfer(
                transactionId, actor.userId(), sender.id(), receiver.id(), amount,
                referenceGenerator.forTransfer(transactionId, now),
                catalog.type(TransactionTypeEnum.TRANSFER), catalog.status(TransactionStatusEnum.PENDING), now));

        // Al final: el código emitido reemplaza al anterior y sale por correo con lo que el cliente está autorizando
        verificationApi.sendTransactionCode(actor.userId(), codeDetail(transaction, receiverNumber));

        TransactionResponse response = responses.of(transaction, senderNumber, receiverNumber);
        response.setExpiresAt(transaction.getCreatedAt().plusMinutes(verificationApi.transactionCodeTtlMinutes()));
        return response;
    }

    /**
     * Segundo paso: verifica el código y, si es correcto, mueve el dinero y completa la transferencia.
     * <p>
     * Un código incorrecto la deja PENDING con los intentos que quedan; al tercer fallo o si el código
     * venció se rechaza y hay que empezar de nuevo. Confirmar una transferencia ya completada devuelve el mismo
     * comprobante sin mover dinero otra vez. Una de otro cliente responde como inexistente.
     * <p>
     * noRollbackFor: los rechazos se registran y los fallos del código cuentan como intento aunque se lance la
     * excepción que se le responde al cliente; sin esto, la transacción se revertiría y nada de eso quedaría guardado.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TransactionResponse confirm(Actor actor, String reference, String code) {
        Transaction transaction = transactionRepository.findByReferenceForUpdate(reference)
                .filter(t -> t.getUserOriginatorId().equals(actor.userId()))
                .orElseThrow(() -> new TransactionNotFoundException("Transacción no encontrada: " + reference));

        if (transaction.is(TransactionStatusEnum.COMPLETED)) {
            return responses.of(transaction);
        }
        if (!transaction.is(TransactionStatusEnum.PENDING)) {
            throw new TransactionNotPendingException("La transferencia " + reference + " ya no se puede confirmar: "
                    + describe(transaction.getStatus().getCode()) + ". Inicia una nueva");
        }

        CodeCheckResult check = verificationApi.verifyTransactionCode(actor.userId(), code);
        switch (check.outcome()) {
            case WRONG -> throw new InvalidTransactionCodeException(
                    "Código incorrecto. " + remaining(check.remainingAttempts()));
            case EXHAUSTED -> {
                reject(transaction);
                throw new TooManyRequestsException(
                        "Superaste el número de intentos: la transferencia fue cancelada. Inicia una nueva", 0);
            }
            case EXPIRED -> {
                reject(transaction);
                throw new TransactionCodeExpiredException(
                        "El código expiró: la transferencia fue cancelada. Inicia el proceso de nuevo");
            }
            case VALID -> {
                return complete(transaction);
            }
        }
        throw new IllegalStateException("Resultado de verificación no previsto: " + check.outcome());
    }

    /**
     * Cancela las transferencias que siguen PENDING pasado el tiempo de vida del código. Es higiene: una confirmación
     * tardía ya se rechaza por código vencido; esto evita que queden pendientes para siempre las que nadie confirmó.
     */
    @Transactional
    public int rejectExpiredPending() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(verificationApi.transactionCodeTtlMinutes());
        return transactionRepository.rejectPendingCreatedBefore(cutoff,
                catalog.status(TransactionStatusEnum.PENDING), catalog.status(TransactionStatusEnum.REJECTED));
    }

    // Mueve el dinero con las dos cuentas bloqueadas y las revalida. Si el saldo o el estado cambiaron desde el primer
    // paso, la transferencia se rechaza y el cliente recibe la causa
    private TransactionResponse complete(Transaction transaction) {
        try {
            accountApi.applyTransfer(transaction.getSenderAccountId(), transaction.getReceiverAccountId(),
                    transaction.getAmount());
        } catch (AccountNotFoundException | AccountNotActiveException | InsufficientFundsException e) {
            reject(transaction);
            throw e;
        }
        transaction.complete(catalog.status(TransactionStatusEnum.COMPLETED));
        transactionRepository.save(transaction);

        TransactionResponse response = responses.of(transaction);
        eventPublisher.publishEvent(new TransferCompletedEvent(transaction.getUserOriginatorId(),
                transaction.getReference(), response.getSenderAccountNumber(), response.getReceiverAccountNumber(),
                transaction.getAmount()));
        return response;
    }

    private void reject(Transaction transaction) {
        transaction.reject(catalog.status(TransactionStatusEnum.REJECTED));
        transactionRepository.save(transaction);
    }

    private void rejectPendingOf(UUID userId) {
        transactionRepository.findPendingOfUserForUpdate(userId, catalog.status(TransactionStatusEnum.PENDING))
                .forEach(this::reject);
    }

    // Se informa la condición de la cuenta de destino, que el cliente necesita para corregir el número
    private AccountView activeReceiver(String receiverNumber) {
        AccountView receiver;
        try {
            receiver = accountApi.getAccountByNumber(receiverNumber);
        } catch (AccountNotFoundException e) {
            throw new InvalidReceiverAccountException("La cuenta de destino no existe");
        }
        if (!receiver.isActive()) {
            throw new InvalidReceiverAccountException(
                    "La cuenta de destino no puede recibir transferencias: " + describe(receiver.status()));
        }
        return receiver;
    }

    private static String codeDetail(Transaction transaction, String receiverNumber) {
        return "Vas a transferir " + transaction.getAmount().toPlainString() + " a la cuenta terminada en "
                + AccountNumbers.lastDigits(receiverNumber) + " (referencia " + transaction.getReference() + ").";
    }

    private static String remaining(int attempts) {
        return attempts == 1 ? "Te queda 1 intento" : "Te quedan " + attempts + " intentos";
    }

    // El estado de una cuenta o transacción en palabras para el cliente
    private static String describe(String status) {
        return switch (status) {
            case "INACTIVE" -> "está inactiva";
            case "BLOCKED" -> "está bloqueada";
            case "CLOSED" -> "está cerrada";
            case "REJECTED" -> "fue cancelada";
            case "REVERSED" -> "fue revertida";
            default -> "su estado es " + status;
        };
    }
}
