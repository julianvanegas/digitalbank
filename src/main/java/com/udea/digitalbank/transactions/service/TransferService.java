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
 * Casos de uso de la transferencia entre cuentas del banco, que se ejecuta en dos pasos:
 * <ol>
 *   <li>{@link #initiate}: valida la operación y la deja {@code PENDING} mientras el segundo factor la autoriza.</li>
 *   <li>{@link #confirm}: verifica el código y, solo entonces, mueve el dinero.</li>
 * </ol>
 *
 * <p><b>Qué se registra.</b> Las validaciones del primer paso fallan sin dejar fila: una transferencia imposible no
 * llega a ser una transacción. Pasan a {@code REJECTED} únicamente las transacciones que ya existían y se cancelan
 * después: código agotado o vencido, reemplazada por una nueva, o inválida al confirmar porque el saldo o el estado
 * de las cuentas ha cambiado.
 *
 * <p><b>Concurrencia.</b> El saldo y el estado de las cuentas se vuelven a validar al confirmar, con las filas
 * bloqueadas, porque pueden cambiar entre los dos pasos.
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
     * Valida la transferencia, la deja {@code PENDING} y envía al correo del ordenante el código de segundo factor
     * que la autoriza. Es el primer paso del flujo; el segundo es {@link #confirm}.
     *
     * <p>Las validaciones se hacen en este orden: cuentas de origen y destino distintas; cuenta de origen del
     * ordenante y activa; cuenta de destino existente y activa; saldo suficiente. La comprobación del saldo permite
     * fallar rápido, pero la definitiva ocurre al confirmar.
     *
     * <p>Si el ordenante ya tenía otra transferencia {@code PENDING}, esa se rechaza: solo hay un código vigente por
     * usuario y el nuevo reemplaza al anterior.
     *
     * @param actor   usuario autenticado que ordena la transferencia
     * @param request cuentas de origen y destino, y monto
     * @return el comprobante en estado {@code PENDING}, con el instante en que vence el código
     * @throws SameAccountTransferException    si origen y destino son la misma cuenta
     * @throws AccountNotFoundException        si la cuenta de origen no existe o no es del ordenante
     * @throws AccountNotActiveException       si la cuenta de origen no está activa
     * @throws InvalidReceiverAccountException si la cuenta de destino no existe o no está activa
     * @throws InsufficientFundsException      si el saldo de la cuenta de origen no alcanza
     */
    @Transactional
    public TransactionResponse initiate(Actor actor, TransferRequest request) {
        String senderNumber = request.getSenderAccountNumber();
        String receiverNumber = request.getReceiverAccountNumber();
        BigDecimal amount = request.getAmount().setScale(2);

        if (senderNumber.equals(receiverNumber)) {
            throw new SameAccountTransferException("No se pueden realizar transferencias a la misma cuenta");
        }

        // Una cuenta ajena responde como inexistente, para no confirmar que el número existe
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

        // El id se asigna antes de guardar porque la referencia (el comprobante) se deriva de él
        UUID transactionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Transaction transaction = transactionRepository.save(Transaction.transfer(
                transactionId, actor.userId(), sender.id(), receiver.id(), amount,
                referenceGenerator.forTransfer(transactionId, now),
                catalog.type(TransactionTypeEnum.TRANSFER), catalog.status(TransactionStatusEnum.PENDING), now));

        // Se envía al final: emitir el código invalida el anterior, y el correo describe la operación que se autoriza
        verificationApi.sendTransactionCode(actor.userId(), codeDetail(transaction, receiverNumber));

        TransactionResponse response = responses.of(transaction, senderNumber, receiverNumber);
        response.setExpiresAt(transaction.getCreatedAt().plusMinutes(verificationApi.transactionCodeTtlMinutes()));
        return response;
    }

    /**
     * Verifica el código de segundo factor y, si es correcto, mueve el dinero y completa la transferencia. Es el
     * segundo paso del flujo, posterior a {@link #initiate}.
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
     * @param actor     usuario autenticado; debe ser el ordenante de la transferencia
     * @param reference comprobante de la transferencia
     * @param code      código de 6 dígitos recibido por correo
     * @return el comprobante de la transferencia ya completada
     * @throws TransactionNotFoundException    si la referencia no existe o pertenece a otro usuario
     * @throws TransactionNotPendingException  si la transferencia está cancelada o revertida
     * @throws InvalidTransactionCodeException si el código es incorrecto y quedan intentos
     * @throws TooManyRequestsException        si se han agotado los intentos; la transferencia queda {@code REJECTED}
     * @throws TransactionCodeExpiredException si el código ha vencido; la transferencia queda {@code REJECTED}
     * @throws InsufficientFundsException      si al mover el dinero el saldo ya no alcanza; queda {@code REJECTED}
     * @throws AccountNotActiveException       si una de las cuentas ya no está activa; queda {@code REJECTED}
     * @throws AccountNotFoundException        si una de las cuentas ya no existe; queda {@code REJECTED}
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
     * Rechaza las transferencias que siguen {@code PENDING} cuando el código de verificación ya ha vencido.
     *
     * <p>Es una limpieza periódica y no una regla de seguridad: una confirmación tardía igualmente se rechaza por
     * código vencido. Evita que queden pendientes indefinidamente las transferencias que nadie confirma.
     *
     * @return cantidad de transferencias rechazadas
     */
    @Transactional
    public int rejectExpiredPending() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(verificationApi.transactionCodeTtlMinutes());
        return transactionRepository.rejectPendingCreatedBefore(cutoff,
                catalog.status(TransactionStatusEnum.PENDING), catalog.status(TransactionStatusEnum.REJECTED));
    }

    /**
     * Mueve el dinero con las dos cuentas bloqueadas, revalidándolas. Si el saldo o el estado cambiaron desde el
     * primer paso, rechaza la transferencia y relanza la causa para que el ordenante la conozca.
     */
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

    /** Rechaza la transacción y guarda el cambio. */
    private void reject(Transaction transaction) {
        transaction.reject(catalog.status(TransactionStatusEnum.REJECTED));
        transactionRepository.save(transaction);
    }

    /** Rechaza las transferencias pendientes del usuario; ver {@code findPendingOfUserForUpdate}. */
    private void rejectPendingOf(UUID userId) {
        transactionRepository.findPendingOfUserForUpdate(userId, catalog.status(TransactionStatusEnum.PENDING))
                .forEach(this::reject);
    }

    /**
     * Busca la cuenta de destino y exige que esté activa. A diferencia de la cuenta de origen, aquí sí se informa la
     * condición (no existe, inactiva, bloqueada o cerrada), porque el ordenante la necesita para corregir el número.
     */
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

    /** Texto del correo con el monto y la cuenta de destino enmascarada, para que el ordenante sepa qué autoriza. */
    private static String codeDetail(Transaction transaction, String receiverNumber) {
        return "Vas a transferir " + transaction.getAmount().toPlainString() + " a la cuenta terminada en "
                + AccountNumbers.lastDigits(receiverNumber) + " (referencia " + transaction.getReference() + ").";
    }

    /** Mensaje de intentos restantes, en singular o plural. */
    private static String remaining(int attempts) {
        return attempts == 1 ? "Te queda 1 intento" : "Te quedan " + attempts + " intentos";
    }

    /** Describe un estado de cuenta o de transacción en palabras, para mostrarlo al ordenante. */
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
