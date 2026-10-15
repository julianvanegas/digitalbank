package com.udea.digitalbank.accounts.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Arma la llave visible de la cuenta con la estructura acordada en CA01:
 * arroba + dos primeras letras del primer nombre + últimos dígitos del documento + posición (1, 2 o 3).
 * Por ejemplo, Ana Gómez con documento 1000000001 y su primera cuenta: {@code @AN0000011}.
 * <p>
 * A diferencia del número de cuenta, esta llave es determinista: se deduce del titular, así que no se
 * puede reintentar con otro valor si choca. Por eso se toman seis dígitos del documento y no tres,
 * según lo decidido tras la reunión: con tres, los nombres en español concentran tanto los prefijos de
 * dos letras que las colisiones aparecen con apenas unos cientos de clientes.
 */
@Component
class AccountKeyGenerator {

    private static final String PREFIX = "@";
    private static final int NAME_CHARS = 2;
    private static final int DOCUMENT_DIGITS = 6;
    // Relleno para un primer nombre de una sola letra, para que la llave conserve su longitud fija
    private static final char NAME_PADDING = 'X';

    String build(String firstNames, String documentNumber, short accountIndex) {
        return PREFIX + namePart(firstNames) + documentPart(documentNumber) + accountIndex;
    }

    /**
     * Las dos primeras letras del primer nombre. "Ana María" toma "AN", no "AN" de "AnaMaría":
     * se queda con el primer nombre y descarta el resto.
     * Las tildes se reducen a su letra base (Álvaro -> AL) para que la llave sea estable y
     * no dependa de cómo se haya escrito el acento.
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
     * Un documento más corto se rellena con ceros a la izquierda para no acortar la llave.
     */
    private static String documentPart(String documentNumber) {
        String digits = documentNumber == null ? "" : documentNumber.replaceAll("\\D", "");
        String last = digits.length() <= DOCUMENT_DIGITS
                ? digits
                : digits.substring(digits.length() - DOCUMENT_DIGITS);
        return "0".repeat(DOCUMENT_DIGITS - last.length()) + last;
    }
}
