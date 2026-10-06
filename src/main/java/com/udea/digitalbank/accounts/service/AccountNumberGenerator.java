package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.repository.AccountRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Genera el número público de la cuenta: 11 dígitos, el primero distinto de cero.
 * Es aleatorio y no secuencial para que nadie pueda deducir los números de las cuentas ajenas;
 * la unicidad la garantiza el UNIQUE de la base y aquí solo se evita el choque conocido.
 */
@Component
class AccountNumberGenerator {

    private static final int DIGITS = 11;
    // Con 9 · 10^10 combinaciones posibles, agotar diez intentos seguidos es inconcebible salvo avería
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom secureRandom = new SecureRandom();
    private final AccountRepository accountRepository;

    AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    String next() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = candidate();
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "No se pudo generar un número de cuenta libre en " + MAX_ATTEMPTS + " intentos");
    }

    private String candidate() {
        StringBuilder number = new StringBuilder(DIGITS);
        number.append(secureRandom.nextInt(1, 10)); // el primer dígito nunca es cero
        for (int i = 1; i < DIGITS; i++) {
            number.append(secureRandom.nextInt(10));
        }
        return number.toString();
    }
}
