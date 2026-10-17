package com.udea.digitalbank.accounts.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Única puerta de entrada de otros módulos a accounts (transactions).
 * Los módulos solo guardan el id de la cuenta; nunca importan entidades ni repositorios de accounts.
 * La implementación vive en accounts.service (AccountApiService).
 */
public interface AccountApi {

    /** Lanza AccountNotFoundException si la cuenta no existe. */
    AccountView getAccount(UUID accountId);

    /** Para operaciones donde el cliente identifica la cuenta por su número de 12 dígitos. */
    AccountView getAccountByNumber(String accountNumber);

    /** Las cuentas del cliente en orden de apertura; lista vacía si no tiene ninguna. */
    List<AccountView> getAccountsOfCustomer(UUID customerId);

    /** Lanza AccountNotFoundException si no existe o AccountNotActiveException si no está ACTIVE. */
    AccountView requireActiveAccount(UUID accountId);

    /**
     * La cuenta con ese número, si es del cliente dueño del usuario, en cualquier estado. Una cuenta ajena responde
     * igual que una inexistente (AccountNotFoundException): así no se confirma que el número existe.
     */
    AccountView requireOwnedAccount(UUID userId, String accountNumber);

    /**
     * Mueve el monto de una cuenta a otra de forma atómica: bloquea las dos filas en un orden fijo, exige que ambas
     * estén ACTIVE y que la emisora tenga saldo, y debita y acredita. Participa en la transacción del llamante: si
     * algo falla después, el movimiento se revierte. Un rechazo (AccountNotFoundException,
     * AccountNotActiveException o InsufficientFundsException) no deja nada a medias y tampoco marca la transacción
     * del llamante para revertir, así que este puede atraparlo y registrar la transferencia como rechazada.
     */
    void applyTransfer(UUID senderAccountId, UUID receiverAccountId, BigDecimal amount);
}
