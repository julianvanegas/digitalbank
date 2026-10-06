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

    // La cuenta de otro cliente se trata como inexistente: un 403 confirmaría que el id existe
    Account ownedBy(UUID accountId, UUID customerId) {
        Account account = byId(accountId);
        if (!account.getCustomerId().equals(customerId)) {
            throw new AccountNotFoundException("Cuenta no encontrada: " + accountId);
        }
        return account;
    }
}
