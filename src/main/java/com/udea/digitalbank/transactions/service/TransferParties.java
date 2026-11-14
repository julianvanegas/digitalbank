package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.shared.exception.accounts.AccountNotActiveException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.exception.accounts.InsufficientFundsException;
import com.udea.digitalbank.shared.exception.transactions.InvalidReceiverAccountException;
import com.udea.digitalbank.shared.exception.transactions.SameAccountTransferException;
import com.udea.digitalbank.shared.exception.transactions.TransactionLimitExceededException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Las partes de una transferencia y lo que depende de quién la ordena: las cuentas de origen y destino, el saldo, los
 * topes y el dueño del código. Lo comparten el inicio y la confirmación de la transferencia.
 *
 * <p>Es, junto con {@link #activeSender}, el único lugar del flujo de transferencias que mira el rol: el resto usa el
 * {@link TransferOperation} que resulta de {@link #operationFor}.
 */
@Component
class TransferParties {

    private final AccountApi accountApi;
    private final TransactionLimitService limitService;

    TransferParties(AccountApi accountApi, TransactionLimitService limitService) {
        this.accountApi = accountApi;
        this.limitService = limitService;
    }

    /**
     * @throws SameAccountTransferException si origen y destino son la misma cuenta
     */
    void requireDifferentAccounts(String senderNumber, String receiverNumber) {
        if (senderNumber.equals(receiverNumber)) {
            throw new SameAccountTransferException("No se pueden realizar transferencias a la misma cuenta");
        }
    }

    /**
     * Busca la cuenta de origen que el ordenante puede usar y exige que esté activa. Un cliente solo puede usar las
     * suyas (una ajena responde como inexistente, para no confirmar que el número existe); un administrador, la de
     * cualquier cliente.
     *
     * @throws AccountNotFoundException  si no existe o el ordenante no puede operar sobre ella
     * @throws AccountNotActiveException si no está activa
     */
    AccountView activeSender(Actor actor, String senderNumber) {
        AccountView sender = actor.admin()
                ? accountApi.getAccountByNumber(senderNumber)
                : accountApi.requireOwnedAccount(actor.userId(), senderNumber);
        if (!sender.isActive()) {
            throw new AccountNotActiveException(
                    "La cuenta de origen no está activa: " + TransferMessages.accountStatus(sender.status()));
        }
        return sender;
    }

    /**
     * Busca la cuenta de destino y exige que esté activa. A diferencia de la cuenta de origen, aquí sí se informa la
     * condición (no existe, inactiva, bloqueada o cerrada), porque el ordenante la necesita para corregir el número.
     *
     * @throws InvalidReceiverAccountException si no existe o no está activa
     */
    AccountView activeReceiver(String receiverNumber) {
        AccountView receiver;
        try {
            receiver = accountApi.getAccountByNumber(receiverNumber);
        } catch (AccountNotFoundException e) {
            throw new InvalidReceiverAccountException("La cuenta de destino no existe");
        }
        if (!receiver.isActive()) {
            throw new InvalidReceiverAccountException("La cuenta de destino no puede recibir transferencias: "
                    + TransferMessages.accountStatus(receiver.status()));
        }
        return receiver;
    }

    /**
     * @throws InsufficientFundsException si el saldo de la cuenta de origen no alcanza
     */
    void requireFunds(AccountView sender, BigDecimal amount) {
        if (sender.balance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Fondos insuficientes en la cuenta de origen");
        }
    }

    /**
     * Resuelve lo que depende de quién ordena. El código es siempre del titular de la cuenta de origen: del propio
     * cliente, o del cliente en cuyo nombre opera un administrador, a quien además no se le aplican los topes.
     */
    TransferOperation operationFor(Actor actor, UUID senderAccountId) {
        return actor.admin()
                ? new TransferOperation(accountApi.getOwnerUserId(senderAccountId), false,
                        TransferMessages.EMPLOYEE_NOTICE)
                : new TransferOperation(actor.userId(), true, "");
    }

    /**
     * Exige que la transferencia respete los topes de la cuenta de origen, si le aplican al ordenante.
     *
     * @throws TransactionLimitExceededException si supera algún tope
     */
    void enforceLimits(TransferOperation operation, AccountView sender, BigDecimal amount) {
        if (operation.enforceLimits()) {
            limitService.requireWithinLimits(sender, TransactionTypeEnum.TRANSFER, amount);
        }
    }

    /**
     * Igual que {@link #enforceLimits(TransferOperation, AccountView, BigDecimal)}, para quien solo conoce el id de la
     * cuenta: la consulta únicamente si los topes aplican, así que un administrador no la paga.
     */
    void enforceLimits(TransferOperation operation, UUID senderAccountId, BigDecimal amount) {
        if (operation.enforceLimits()) {
            limitService.requireWithinLimits(accountApi.getAccount(senderAccountId), TransactionTypeEnum.TRANSFER,
                    amount);
        }
    }
}
