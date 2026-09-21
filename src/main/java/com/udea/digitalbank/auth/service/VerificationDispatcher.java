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

    // Devuelve el id del reto: el flujo LOGIN lo entrega al cliente
    public IssuedChallenge issueAndSend(User user, PurposeEnum purpose) {
        IssuedChallenge challenge = verificationService.issue(user, purpose);
        send(user, purpose, challenge);
        return challenge;
    }

    // Para los flujos públicos que no deben permitir llenar la bandeja de otra persona:
    // si el último código es muy reciente, no emite ni envía nada
    public void issueAndSendThrottled(User user, PurposeEnum purpose) {
        verificationService.issueThrottled(user, purpose)
                .ifPresent(challenge -> send(user, purpose, challenge));
    }

    private void send(User user, PurposeEnum purpose, IssuedChallenge challenge) {
        emailService.send(user.getEmail(), subject(purpose),
                "Tu código es: " + challenge.code() + ". Expira en " + challenge.ttlMinutes() + " minutos.");
    }

    private static String subject(PurposeEnum purpose) {
        return switch (purpose) {
            case LOGIN -> "Tu código de verificación";
            case EMAIL_CONFIRMATION -> "Confirma tu correo electrónico";
            case PASSWORD_RESET -> "Recuperación de contraseña";
        };
    }
}
