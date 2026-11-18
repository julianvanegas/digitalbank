package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Búsquedas de cuentas compartidas por AccountService (uso interno) y AccountApiService (contrato
 * para otros módulos): así el mensaje de "no encontrada" vive en un solo lugar.
 */
@Component
class AccountLookup {

    private static final String NOT_FOUND = "Cuenta no encontrada: ";

    private final AccountRepository accountRepository;

    AccountLookup(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    Account byId(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(NOT_FOUND + accountId));
    }

    Account byNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(NOT_FOUND + accountNumber));
    }

    // Para los casos de uso que cambian el estado de la cuenta (bloquear, desbloquear): dos peticiones
    // simultáneas se turnan en vez de leer el mismo estado y registrar la misma acción dos veces
    Account byNumberForUpdate(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(NOT_FOUND + accountNumber));
    }

    // La cuenta de otro cliente se trata como inexistente: un 403 confirmaría que el número existe.
    // El mensaje lleva el número que envía el cliente, nunca el id interno: así "no existe" y
    // "es de otro cliente" producen exactamente la misma respuesta
    Account byNumberOwnedBy(String accountNumber, UUID customerId) {
        return requireOwner(byNumber(accountNumber), accountNumber, customerId);
    }

    // Titular de la cuenta sin cargar la entidad, para bloquear después todas sus cuentas
    UUID ownerOf(String accountNumber) {
        return accountRepository.findCustomerIdByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(NOT_FOUND + accountNumber));
    }

    // Igual que ownerOf, pero una cuenta de otro cliente responde igual que una inexistente
    UUID ownerOf(String accountNumber, UUID customerId) {
        UUID owner = ownerOf(accountNumber);
        if (!owner.equals(customerId)) {
            throw new AccountNotFoundException(NOT_FOUND + accountNumber);
        }
        return owner;
    }

    // La cuenta con ese número entre las del titular ya bloqueadas
    static Account byNumberIn(List<Account> accounts, String accountNumber) {
        return accounts.stream()
                .filter(account -> account.getAccountNumber().equals(accountNumber))
                .findFirst()
                .orElseThrow(() -> new AccountNotFoundException(NOT_FOUND + accountNumber));
    }

    private static Account requireOwner(Account account, String accountNumber, UUID customerId) {
        if (!account.getCustomerId().equals(customerId)) {
            throw new AccountNotFoundException(NOT_FOUND + accountNumber);
        }
        return account;
    }
}
