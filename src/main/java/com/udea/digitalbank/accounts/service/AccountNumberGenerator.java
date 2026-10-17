package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.repository.AccountRepository;
import org.springframework.stereotype.Component;

/**
 * Genera el número público de la cuenta: 12 dígitos, el primero distinto de cero.
 * <p>
 * Cada número sale del siguiente valor de la secuencia de la base, pasado por una permutación secreta
 * ({@link AccountNumberPermutation}). Así es único por construcción, sin sortear ni consultar si ya existe, y no
 * es secuencial para que nadie pueda deducir los números de las cuentas ajenas. El UNIQUE de la base queda como
 * garantía final, por ejemplo frente a un número sorteado al azar antes de esta secuencia.
 */
@Component
class AccountNumberGenerator {

    private final AccountRepository accountRepository;
    private final AccountNumberPermutation permutation;

    AccountNumberGenerator(AccountRepository accountRepository, AccountNumberPermutation permutation) {
        this.accountRepository = accountRepository;
        this.permutation = permutation;
    }

    String next() {
        return permutation.apply(accountRepository.nextAccountNumberValue());
    }
}
