package com.udea.digitalbank.shared.exception.accounts;

import com.udea.digitalbank.shared.exception.BusinessException;

/**
 * No queda consecutivo disponible para el prefijo de la llave sin superar el largo de la columna.
 * Con un prefijo de seis caracteres haría falta un consecutivo de 58 dígitos, así que en la práctica
 * no ocurre: existe para que el caso no pase inadvertido si alguna vez se acorta la columna.
 */
public class AccountKeyCollisionException extends BusinessException {
    public AccountKeyCollisionException(String message) {
        super(Kind.CONFLICT, message);
    }
}
