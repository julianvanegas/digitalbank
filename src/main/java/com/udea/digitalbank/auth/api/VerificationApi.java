package com.udea.digitalbank.auth.api;

import java.util.UUID;

/**
 * Segundo factor (2FA) de las operaciones monetarias, ofrecido a otros módulos (por ejemplo {@code transactions}).
 *
 * <p>El módulo que llama solo conoce el id del usuario: el código, su vigencia y su límite de intentos los gestiona
 * {@code auth}. Hay un único código de transacción vigente por usuario; emitir uno nuevo invalida el anterior.
 *
 * <p>Como el resto de fachadas, no expone entidades ni repositorios de {@code auth}. La implementación es
 * {@code VerificationApiService}.
 */
public interface VerificationApi {

    /**
     * Emite un código nuevo para el usuario y lo envía a su correo; el anterior deja de ser válido. Participa en la
     * transacción del llamante.
     *
     * @param userId usuario que debe autorizar la operación
     * @param detail descripción de la operación, que se incluye en el correo para que el usuario sepa qué autoriza
     * @throws com.udea.digitalbank.shared.exception.auth.UserNotFoundException si el usuario no existe
     */
    void sendTransactionCode(UUID userId, String detail);

    /**
     * Verifica el código vigente del usuario. Un código inválido no lanza excepción: se informa en el resultado
     * (correcto, incorrecto con intentos restantes, intentos agotados o vencido) para que el módulo que llama decida
     * qué hacer con la operación que el código autorizaba.
     *
     * <p>Un código correcto se consume: no puede usarse dos veces. Un fallo cuenta como intento, y al agotarlos el
     * código se invalida.
     *
     * @param userId usuario que autoriza la operación
     * @param code   código recibido por correo
     * @return el resultado de la verificación; si no hay un código vigente, {@code EXPIRED}
     */
    CodeCheckResult verifyTransactionCode(UUID userId, String code);

    /**
     * @return minutos durante los cuales el código de transacción es válido desde que se emite
     */
    int transactionCodeTtlMinutes();
}
