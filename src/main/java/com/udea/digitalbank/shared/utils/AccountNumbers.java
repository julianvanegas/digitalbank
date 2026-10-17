package com.udea.digitalbank.shared.utils;

/**
 * Formato de un número de cuenta para mostrarlo fuera de la API, por ejemplo en correos y mensajes: solo se muestran
 * los últimos dígitos. El titular reconoce cuál de sus cuentas es sin que el correo lleve el número completo.
 */
public final class AccountNumbers {

    /** Cantidad de dígitos finales que se muestran. */
    private static final int VISIBLE_DIGITS = 4;

    private AccountNumbers() {
    }

    /**
     * @param accountNumber número de cuenta completo
     * @return los últimos 4 dígitos; si el número tiene 4 o menos, se devuelve completo
     */
    public static String lastDigits(String accountNumber) {
        return accountNumber.length() <= VISIBLE_DIGITS
                ? accountNumber
                : accountNumber.substring(accountNumber.length() - VISIBLE_DIGITS);
    }
}
