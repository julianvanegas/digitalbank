package com.udea.digitalbank.auth.api;

import java.util.UUID;

/**
 * Segundo factor (2FA) de las operaciones monetarias, para otros módulos (transactions). El módulo que llama solo
 * conoce el id del usuario: el código, su vigencia y sus intentos los gestiona auth. Hay un solo código vigente
 * por usuario: emitir uno nuevo reemplaza el anterior. La implementación vive en auth.service
 * (VerificationApiService).
 */
public interface VerificationApi {

    /**
     * Emite un código nuevo para el usuario y lo envía a su correo; el anterior deja de valer. El detalle se
     * incluye en el correo para que el titular sepa qué operación autoriza. Participa en la transacción del llamante.
     */
    void sendTransactionCode(UUID userId, String detail);

    /**
     * Verifica el código. Nunca lanza por un código malo: devuelve qué ocurrió (correcto, incorrecto con intentos
     * restantes, intentos agotados o vencido) y el módulo que llama decide qué hacer con la operación.
     */
    CodeCheckResult verifyTransactionCode(UUID userId, String code);

    /** Vigencia del código, en minutos. */
    int transactionCodeTtlMinutes();
}
