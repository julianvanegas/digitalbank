package com.udea.digitalbank.shared.exception.transactions;

import com.udea.digitalbank.shared.exception.BusinessException;

/**
 * La cuenta de destino no existe o no está activa, así que no puede recibir la transferencia. El mensaje
 * indica la condición para que el ordenante pueda corregir el número. Responde 400.
 */
public class InvalidReceiverAccountException extends BusinessException {
    public InvalidReceiverAccountException(String message) {
        super(Kind.INVALID, message);
    }
}
