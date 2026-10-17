package com.udea.digitalbank.accounts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * Convierte el valor de la secuencia en un número de cuenta de 12 dígitos que no deja deducir cuántas cuentas hay ni
 * cuál es la contigua. Es una permutación del rango de números válidos: {@code (n · K + C) mod N}, con
 * N = 9 · 10^11 (los 12 dígitos con el primero distinto de cero) y K coprimo con N. Mientras K y N no compartan
 * divisores, cada n distinto da un número distinto: la unicidad queda garantizada por construcción, sin consultar
 * la tabla.
 * <p>
 * K y C salen de un secreto del servidor. Es una ofuscación, no un cifrado: con muchos pares (n, número) conocidos
 * se podrían despejar. Quien no los tenga no puede predecir el número de una cuenta ajena, que es lo que se busca;
 * el número no es un secreto ni protege el acceso a la cuenta (eso lo hace la clave de la cuenta).
 */
@Component
class AccountNumberPermutation {

    static final int DIGITS = 12;
    // Primer número válido: 100000000000 (el primer dígito nunca es cero)
    static final long FIRST = 100_000_000_000L;
    // Cuántos números válidos hay: de 100000000000 a 999999999999. También es el máximo de la secuencia
    static final long SIZE = 900_000_000_000L;

    private static final BigInteger N = BigInteger.valueOf(SIZE);
    private static final String ALGORITHM = "HmacSHA256";

    private final BigInteger multiplier;
    private final BigInteger offset;

    AccountNumberPermutation(@Value("${account.number-secret}") String secret) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        this.multiplier = coprimeWithSize(derive(key, "multiplier"));
        this.offset = derive(key, "offset");
    }

    /** El número de cuenta (12 dígitos) que corresponde al valor de la secuencia, de 1 a {@link #SIZE}. */
    String apply(long sequenceValue) {
        if (sequenceValue < 1 || sequenceValue > SIZE) {
            throw new IllegalArgumentException("Valor de la secuencia fuera de rango: " + sequenceValue);
        }
        long position = BigInteger.valueOf(sequenceValue).multiply(multiplier).add(offset).mod(N).longValueExact();
        return Long.toString(FIRST + position);
    }

    // Un valor derivado del secreto y una etiqueta, dentro de [0, N)
    private static BigInteger derive(byte[] key, String label) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return new BigInteger(1, mac.doFinal(label.getBytes(StandardCharsets.UTF_8))).mod(N);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo derivar la permutación de números de cuenta", e);
        }
    }

    // N = 2^11 · 3^2 · 5^11, así que K debe evitar los factores 2, 3 y 5. Se avanza hasta el primer coprimo, que
    // aparece en pocos pasos. Con K = 0 o 1 no habría permutación útil, por eso nunca se parte de ahí
    private static BigInteger coprimeWithSize(BigInteger candidate) {
        BigInteger k = candidate.max(BigInteger.TWO);
        while (!k.gcd(N).equals(BigInteger.ONE)) {
            k = k.add(BigInteger.ONE);
        }
        return k;
    }
}
