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
     * Devuelve la cuenta con ese número si pertenece al cliente dueño del usuario, en cualquier estado. Una cuenta
     * ajena responde igual que una inexistente, para no confirmar que el número existe.
     *
     * @param userId        usuario autenticado
     * @param accountNumber número de la cuenta (12 dígitos)
     * @return la vista de la cuenta
     * @throws com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException si la cuenta no existe o es de
     *         otro cliente
     */
    AccountView requireOwnedAccount(UUID userId, String accountNumber);

    /**
     * Mueve un monto de una cuenta a otra de forma atómica. Bloquea las dos filas en un orden fijo (por id), exige que
     * ambas estén {@code ACTIVE} y que la cuenta de origen tenga saldo, y entonces debita y acredita.
     *
     * <p><b>Transaccionalidad.</b> Participa en la transacción del llamante: si algo falla después, el movimiento se
     * revierte. Un rechazo no deja nada a medias, porque las dos cuentas se validan antes de modificar cualquiera, y
     * tampoco marca la transacción del llamante para revertir; así este puede atrapar la excepción y registrar la
     * transferencia como rechazada.
     *
     * @param senderAccountId   cuenta que entrega el dinero
     * @param receiverAccountId cuenta que lo recibe; debe ser distinta de la de origen
     * @param amount            monto positivo
     * @throws com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException  si alguna cuenta no existe
     * @throws com.udea.digitalbank.shared.exception.accounts.AccountNotActiveException si alguna cuenta no está activa
     * @throws com.udea.digitalbank.shared.exception.accounts.InsufficientFundsException si el saldo de origen no
     *         alcanza
     * @throws IllegalArgumentException si ambas cuentas son la misma o el monto no es positivo
     */
    void applyTransfer(UUID senderAccountId, UUID receiverAccountId, BigDecimal amount);
}
