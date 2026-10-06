package com.udea.digitalbank.accounts.api;

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

    /** Para operaciones donde el cliente identifica la cuenta por su número de 11 dígitos. */
    AccountView getAccountByNumber(String accountNumber);

    /** Las cuentas del cliente en orden de apertura; lista vacía si no tiene ninguna. */
    List<AccountView> getAccountsOfCustomer(UUID customerId);

    /** Lanza AccountNotFoundException si no existe o AccountNotActiveException si no está ACTIVE. */
    AccountView requireActiveAccount(UUID accountId);
}
