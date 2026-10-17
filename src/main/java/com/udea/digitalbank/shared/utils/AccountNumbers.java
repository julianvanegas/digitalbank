package com.udea.digitalbank.shared.utils;

/**
 * Cómo se muestra un número de cuenta fuera de la API: en correos y mensajes solo van los últimos dígitos. El titular
 * distingue cuál de sus cuentas es sin que el correo lleve el número completo.
 */
public final class AccountNumbers {

    private static final int VISIBLE_DIGITS = 4;

    private AccountNumbers() {
    }

    /** Los últimos 4 dígitos; un número más corto se devuelve entero. */
    public static String lastDigits(String accountNumber) {
        return accountNumber.length() <= VISIBLE_DIGITS
                ? accountNumber
                : accountNumber.substring(accountNumber.length() - VISIBLE_DIGITS);
    }
}
