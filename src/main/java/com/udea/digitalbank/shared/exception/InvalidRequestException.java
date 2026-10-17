package com.udea.digitalbank.shared.exception;

/**
 * Un dato de la petición no cumple una regla de negocio (edad mínima, contraseña y confirmación distintas, un
 * motivo obligatorio vacío...). Responde 400 con su mensaje, que es para el cliente. Es distinta de
 * IllegalArgumentException, que queda para errores de programación y responde 500.
 */
public class InvalidRequestException extends BusinessException {
    public InvalidRequestException(String message) {
        super(Kind.INVALID, message);
    }
}
