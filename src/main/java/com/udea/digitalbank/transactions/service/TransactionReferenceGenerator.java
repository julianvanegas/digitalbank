package com.udea.digitalbank.transactions.service;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Arma la referencia de una transferencia, que es su comprobante: TRF-AAAAMMDD-{UUID en base58}, por ejemplo
 * TRF-20261017-7Xk2Pq9RmZ4tNvB8cHdWeA. La parte final es el UUID completo de la transacción escrito en 22
 * caracteres, así que la referencia lleva a su fila y es única por construcción: dos UUID distintos nunca dan el
 * mismo texto, y no se sortea ni se consulta si ya existe. La fecha es la de creación de la transacción.
 * <p>
 * El alfabeto es base58: letras y dígitos sin símbolos, así que no choca con el "-" que separa las partes ni
 * necesita escape en la ruta, y sin los caracteres que se confunden al dictar o leer (0/O, I/l). Distingue
 * mayúsculas de minúsculas. 58^22 supera 2^128, por eso 22 caracteres bastan para cualquier UUID.
 */
@Component
class TransactionReferenceGenerator {

    private static final String PREFIX = "TRF-";
    static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    static final int CODE_LENGTH = 22;
    private static final BigInteger BASE = BigInteger.valueOf(ALPHABET.length());

    /**
     * Arma la referencia de una transferencia.
     *
     * @param transactionId identificador de la transacción
     * @param createdAt     instante de creación; aporta la fecha de la referencia
     * @return la referencia, de 35 caracteres
     */
    String forTransfer(UUID transactionId, LocalDateTime createdAt) {
        return PREFIX + createdAt.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE) + '-'
                + encode(transactionId);
    }

    /**
     * Escribe el UUID como número sin signo de 128 bits en base 58, rellenado a 22 caracteres con el símbolo del
     * cero.
     */
    private static String encode(UUID id) {
        byte[] bytes = ByteBuffer.allocate(Long.BYTES * 2)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
        BigInteger value = new BigInteger(1, bytes);
        char[] code = new char[CODE_LENGTH];
        for (int i = CODE_LENGTH - 1; i >= 0; i--) {
            BigInteger[] divided = value.divideAndRemainder(BASE);
            code[i] = ALPHABET.charAt(divided[1].intValue());
            value = divided[0];
        }
        return new String(code);
    }
}
