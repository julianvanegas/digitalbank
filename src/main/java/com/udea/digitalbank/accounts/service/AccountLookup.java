package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Búsquedas de cuentas compartidas por AccountService (uso interno) y AccountApiService (contrato
 * para otros módulos): así el mensaje de "no encontrada" vive en un solo lugar.
 */
@Component
class AccountLookup {

    private final AccountRepository accountRepository;

    AccountLookup(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    Account byId(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada: " + accountId));
    }

    Account byNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada: " + accountNumber));
    }

    // Para los casos de uso que cambian el estado de la cuenta (bloquear, desbloquear): dos peticiones
    // simultáneas se turnan en vez de leer el mismo estado y registrar la misma acción dos veces
    Account byNumberForUpdate(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada: " + accountNumber));
    }

    // La cuenta de otro cliente se trata como inexistente: un 403 confirmaría que el número existe.
    // El mensaje lleva el número que envió el cliente, nunca el id interno: así "no existe" y
    // "es de otro cliente" producen exactamente la misma respuesta
    Account byNumberOwnedBy(String accountNumber, UUID customerId) {
        return requireOwner(byNumber(accountNumber), accountNumber, customerId);
    }

    // Igual que byNumberOwnedBy, pero con la fila bloqueada: para cambiar el estado de la cuenta del titular
    Account byNumberOwnedByForUpdate(String accountNumber, UUID customerId) {
        return requireOwner(byNumberForUpdate(accountNumber), accountNumber, customerId);
    }

    private static Account requireOwner(Account account, String accountNumber, UUID customerId) {
        if (!account.getCustomerId().equals(customerId)) {
            throw new AccountNotFoundException("Cuenta no encontrada: " + accountNumber);
        }
        return account;
    }
}
