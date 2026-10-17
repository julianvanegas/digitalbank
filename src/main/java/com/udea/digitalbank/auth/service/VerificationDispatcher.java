package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.shared.utils.EmailService;
import org.springframework.stereotype.Service;

/**
 * Emite un reto de verificación y envía su código al email del usuario. El propósito se indica
 * una sola vez, así el reto emitido y el asunto del correo no pueden desalinearse.
 */
@Service
public class VerificationDispatcher {

    private final VerificationService verificationService;
    private final EmailService emailService;

    public VerificationDispatcher(VerificationService verificationService, EmailService emailService) {
        this.verificationService = verificationService;
        this.emailService = emailService;
    }

    /** Devuelve el id del reto: el flujo LOGIN lo entrega al cliente. */
    public IssuedChallenge issueAndSend(User user, PurposeEnum purpose) {
        return issueAndSend(user, purpose, null);
    }

    /**
     * Emite el código y lo envía por correo, precedido de una descripción de la operación que autoriza.
     *
     * @param user    destinatario del código
     * @param purpose propósito del código
     * @param detail  descripción de la operación (por ejemplo, el monto de una transferencia); puede ser {@code null}
     * @return el código emitido
     */
    public IssuedChallenge issueAndSend(User user, PurposeEnum purpose, String detail) {
        IssuedChallenge challenge = verificationService.issue(user, purpose);
        send(user, purpose, challenge, detail);
        return challenge;
    }

    /**
     * Para los flujos públicos que no deben permitir llenar la bandeja de otra persona:
     * si el último código es muy reciente, no emite ni envía nada.
     */
    public void issueAndSendThrottled(User user, PurposeEnum purpose) {
        verificationService.issueThrottled(user, purpose)
                .ifPresent(challenge -> send(user, purpose, challenge));
    }

    /** Envía un código que VerificationService ya ha emitido (p. ej. al reenviarlo). */
    public void send(User user, PurposeEnum purpose, IssuedChallenge challenge) {
        send(user, purpose, challenge, null);
    }

    /** Arma y envía el correo; si hay detalle, va antes del código. */
    private void send(User user, PurposeEnum purpose, IssuedChallenge challenge, String detail) {
        String body = "Tu código es: " + challenge.code() + ". Expira en " + challenge.ttlMinutes() + " minutos.";
        emailService.send(user.getEmail(), subject(purpose), detail == null ? body : detail + "\n\n" + body);
    }

    private static String subject(PurposeEnum purpose) {
        return switch (purpose) {
            case LOGIN -> "Tu código de verificación";
            case EMAIL_CONFIRMATION -> "Confirma tu correo electrónico";
            case PASSWORD_RESET -> "Recuperación de contraseña";
            case TRANSACTION -> "Confirma tu transacción";
        };
    }
}
