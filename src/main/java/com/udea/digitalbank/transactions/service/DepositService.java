package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.shared.exception.accounts.AccountNotActiveException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.exception.transactions.InvalidReceiverAccountException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.dto.DepositRequest;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Caso de uso del depósito (consignación) a una cuenta del banco, que registra un administrador.
 *
 * <p>A diferencia de la transferencia, se ejecuta en un solo paso y sin segundo factor: el administrador lo registra
 * cuando la fuente de pago ya autorizó el ingreso. El saldo se acredita y la transacción queda {@code COMPLETED} en
 * la misma transacción de base de datos; si algo falla, no queda ni el movimiento ni la fila.
 *
 * <p>Una consignación rechazada no se registra: las validaciones fallan antes de crear la transacción.
 */
@Service
public class DepositService {

    private final TransactionRepository transactionRepository;
    private final TransactionCatalog catalog;
    private final TransactionResponses responses;
    private final TransactionReferenceGenerator referenceGenerator;
    private final AccountApi accountApi;

    public DepositService(TransactionRepository transactionRepository,
                          TransactionCatalog catalog,
                          TransactionResponses responses,
                          TransactionReferenceGenerator referenceGenerator,
                          AccountApi accountApi) {
        this.transactionRepository = transactionRepository;
        this.catalog = catalog;
        this.responses = responses;
        this.referenceGenerator = referenceGenerator;
        this.accountApi = accountApi;
    }

    /**
     * Acredita el monto a la cuenta de destino y genera el comprobante del depósito.
     *
     * <p>El estado de la cuenta se comprueba primero para responder con un mensaje claro, y se vuelve a comprobar al
     * acreditar, con la fila bloqueada, por si cambia entre medias (por ejemplo, otro administrador la bloquea).
     *
     * @param actor   administrador autenticado que registra el depósito
     * @param request cuenta de destino y monto
     * @return el comprobante del depósito, en estado {@code COMPLETED}
     * @throws InvalidReceiverAccountException si la cuenta de destino no existe o no está activa (bloqueada, cerrada o
     *                                         inactiva)
     */
    @Transactional
    public TransactionResponse deposit(Actor actor, DepositRequest request) {
        String receiverNumber = request.getReceiverAccountNumber();
        BigDecimal amount = request.getAmount().setScale(2);

        AccountView receiver = activeReceiver(receiverNumber);
        try {
            accountApi.applyDeposit(receiver.id(), amount);
        } catch (AccountNotFoundException e) {
            throw new InvalidReceiverAccountException("La cuenta no existe");
        } catch (AccountNotActiveException e) {
            throw new InvalidReceiverAccountException("La cuenta no permite créditos: no está activa");
        }

        // El id se asigna antes de guardar porque la referencia (el comprobante) se deriva de él
        UUID transactionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Transaction transaction = transactionRepository.save(Transaction.deposit(
                transactionId, actor.userId(), receiver.id(), amount,
                referenceGenerator.forDeposit(transactionId, now),
                catalog.type(TransactionTypeEnum.DEPOSIT), catalog.status(TransactionStatusEnum.COMPLETED), now));

        return responses.of(transaction, null, receiverNumber);
    }

    /**
     * Busca la cuenta de destino y exige que esté activa. El mensaje indica la condición (no existe, bloqueada,
     * cerrada o inactiva) para que el administrador sepa por qué se rechaza.
     */
    private AccountView activeReceiver(String receiverNumber) {
        AccountView receiver;
        try {
            receiver = accountApi.getAccountByNumber(receiverNumber);
        } catch (AccountNotFoundException e) {
            throw new InvalidReceiverAccountException("La cuenta no existe");
        }
        if (!receiver.isActive()) {
            throw new InvalidReceiverAccountException("La cuenta no permite créditos: " + describe(receiver.status()));
        }
        return receiver;
    }

    /** Describe el estado de la cuenta en palabras, para mostrarlo al administrador. */
    private static String describe(String status) {
        return switch (status) {
            case "INACTIVE" -> "está inactiva";
            case "BLOCKED" -> "está bloqueada";
            case "CLOSED" -> "está cerrada";
            default -> "su estado es " + status;
        };
    }
}
