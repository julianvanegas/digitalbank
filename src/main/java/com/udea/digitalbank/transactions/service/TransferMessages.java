package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.shared.utils.AccountNumbers;

import java.math.BigDecimal;

/**
 * Textos que el flujo de transferencias muestra al ordenante y al titular: el correo del código, los intentos que
 * quedan y el estado de una cuenta en palabras. Viven aparte de los servicios de transferencia para que el caso de uso no
 * cambie cuando cambia la redacción.
 */
final class TransferMessages {

    /** Aviso con el que el correo del código aclara que la operación la inició un empleado y no el titular. */
    static final String EMPLOYEE_NOTICE = "Un empleado del banco inició esta operación a tu nombre. ";

    private TransferMessages() {
    }

    /**
     * Texto del correo del código: el monto y la cuenta de destino enmascarada, para que el titular sepa qué autoriza.
     *
     * @param prefix         aviso previo, por ejemplo {@link #EMPLOYEE_NOTICE}; vacío si la ordena el titular
     * @param amount         monto de la transferencia
     * @param receiverNumber número completo de la cuenta de destino; solo se muestran sus últimos dígitos
     * @param reference      comprobante de la transferencia
     */
    static String codeDetail(String prefix, BigDecimal amount, String receiverNumber, String reference) {
        return prefix + "Vas a transferir " + amount.toPlainString() + " a la cuenta terminada en "
                + AccountNumbers.lastDigits(receiverNumber) + " (referencia " + reference + ").";
    }

    /** Mensaje de intentos restantes, en singular o plural. */
    static String remainingAttempts(int attempts) {
        return attempts == 1 ? "Te queda 1 intento" : "Te quedan " + attempts + " intentos";
    }

    /** Describe un estado de cuenta en palabras, para mostrarlo al ordenante. */
    static String accountStatus(String status) {
        return switch (status) {
            case "INACTIVE" -> "está inactiva";
            case "BLOCKED" -> "está bloqueada";
            case "CLOSED" -> "está cerrada";
            default -> "su estado es " + status;
        };
    }
}
