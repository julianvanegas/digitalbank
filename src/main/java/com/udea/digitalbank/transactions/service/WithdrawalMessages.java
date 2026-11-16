package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.shared.utils.AccountNumbers;

import java.math.BigDecimal;

/**
 * Textos que el flujo de retiro (HU19) muestra al ordenante y al titular: el correo del código y los rechazos. Viven
 * aparte del servicio, como {@link TransferMessages} en las transferencias, para que el caso de uso no cambie cuando
 * cambia la redacción. Los textos neutros (el aviso de un empleado, el estado de una cuenta) se toman de
 * {@link TransferMessages} en vez de repetirlos.
 */
final class WithdrawalMessages {

    private WithdrawalMessages() {
    }

    /**
     * Texto del correo del código: el monto y la cuenta enmascarada, para que el titular sepa qué autoriza.
     *
     * @param prefix        aviso previo, por ejemplo {@link TransferMessages#EMPLOYEE_NOTICE}; vacío si lo ordena el
     *                      titular
     * @param amount        monto del retiro
     * @param accountNumber número completo de la cuenta; solo se muestran sus últimos dígitos
     * @param reference     comprobante del retiro
     */
    static String codeDetail(String prefix, BigDecimal amount, String accountNumber, String reference) {
        return prefix + "Vas a retirar " + amount.toPlainString() + " de la cuenta terminada en "
                + AccountNumbers.lastDigits(accountNumber) + " (referencia " + reference + ").";
    }

    /** Rechazo de un retiro sobre una cuenta que no está activa. */
    static String accountNotActive(String status) {
        return "No se puede retirar de la cuenta: " + TransferMessages.accountStatus(status);
    }

    /** Rechazo de un retiro mayor que el saldo (CA02). */
    static final String INSUFFICIENT_FUNDS = "Saldo insuficiente para realizar el retiro";

    /** Código incorrecto con intentos disponibles (CA04): el retiro sigue pendiente. */
    static String wrongCode(int remainingAttempts) {
        return "Código incorrecto. " + TransferMessages.remainingAttempts(remainingAttempts);
    }

    /** Último intento fallido (CA04): el retiro se cancela. */
    static final String EXHAUSTED_ATTEMPTS =
            "Superaste el número de intentos: el retiro fue cancelado. Inicia uno nuevo";

    /** Código vencido o inexistente: el retiro se cancela. */
    static final String EXPIRED_CODE = "El código expiró: el retiro fue cancelado. Inicia el proceso de nuevo";
}
