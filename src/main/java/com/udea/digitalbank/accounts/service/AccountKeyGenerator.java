package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.shared.exception.accounts.AccountKeyCollisionException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Arma la llave visible de la cuenta: arroba + dos primeras letras del primer nombre + tres últimos
 * dígitos del documento + un consecutivo. Por ejemplo, Ana Gómez con documento 1000000001 obtiene
 * {@code @AN0011} en su primera cuenta.
 * <p>
 * El consecutivo no es la posición de la cuenta dentro del cliente, sino el primer número libre para
 * ese prefijo: si dos clientes distintos coinciden en nombre y documento, el segundo continúa la
 * numeración del primero en vez de chocar. Así la colisión deja de ser un error y se vuelve el
 * mecanismo normal de asignación.
 */
@Component
class AccountKeyGenerator {

    private static final String PREFIX = "@";
    private static final int NAME_CHARS = 2;
    private static final int DOCUMENT_DIGITS = 3;
    // Relleno para un primer nombre de una sola letra, para que el prefijo conserve su longitud
    private static final char NAME_PADDING = 'X';

    private final AccountRepository accountRepository;

    AccountKeyGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    String build(String firstNames, String documentNumber) {
        String prefix = PREFIX + namePart(firstNames) + documentPart(documentNumber);
        String key = prefix + nextConsecutive(prefix);

        // Con un prefijo de seis caracteres, agotar el espacio exigiría un consecutivo de 58 dígitos:
        // en la práctica no ocurre, pero la llave no puede exceder la columna.
        if (key.length() > Account.MAX_KEY_LENGTH) {
            throw new AccountKeyCollisionException("No queda consecutivo disponible para la llave "
                    + prefix + ": se superó la longitud máxima de " + Account.MAX_KEY_LENGTH + " caracteres");
        }
        return key;
    }

    /**
     * El primer consecutivo libre para el prefijo, empezando en 1. Se traen de una sola consulta las
     * llaves que ya lo usan en vez de preguntar número por número.
     */
    private int nextConsecutive(String prefix) {
        Set<String> taken = new HashSet<>();
        for (String key : accountRepository.findKeysByPrefix(prefix)) {
            taken.add(key.substring(prefix.length()));
        }

        int consecutive = 1;
        while (taken.contains(String.valueOf(consecutive))) {
            consecutive++;
        }
        return consecutive;
    }

    /**
     * Las dos primeras letras del primer nombre. "Ana María" toma "AN": se queda con el primer nombre
     * y descarta el resto. Las tildes se reducen a su letra base (Álvaro -> AL) para que la llave no
     * dependa de cómo se haya escrito el acento.
     */
    private static String namePart(String firstNames) {
        String firstName = firstNames == null ? "" : firstNames.trim().split("\\s+")[0];
        String ascii = Normalizer.normalize(firstName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")      // quita los acentos ya separados de la letra
                .replaceAll("[^A-Za-z]", "");  // la ñ se vuelve n en NFD; lo demás se descarta
        String upper = ascii.toUpperCase(Locale.ROOT);

        StringBuilder part = new StringBuilder(NAME_CHARS);
        part.append(upper, 0, Math.min(NAME_CHARS, upper.length()));
        while (part.length() < NAME_CHARS) {
            part.append(NAME_PADDING);
        }
        return part.toString();
    }

    /**
     * Los últimos dígitos del documento. Se descarta cualquier carácter que no sea dígito, porque el
     * documento se guarda tal como lo escribe el cliente y puede traer puntos o letras (pasaporte).
     * Un documento más corto se rellena con ceros a la izquierda para no acortar el prefijo.
     */
    private static String documentPart(String documentNumber) {
        String digits = documentNumber == null ? "" : documentNumber.replaceAll("\\D", "");
        String last = digits.length() <= DOCUMENT_DIGITS
                ? digits
                : digits.substring(digits.length() - DOCUMENT_DIGITS);
        return "0".repeat(DOCUMENT_DIGITS - last.length()) + last;
    }
}
