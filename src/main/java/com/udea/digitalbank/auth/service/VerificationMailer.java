package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.PurposeCode;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class VerificationMailer {

    private final JavaMailSender mailSender;

    public VerificationMailer(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(String to, PurposeCode purposeCode, IssuedChallenge challenge) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject(purposeCode));
        message.setText("Tu código es: " + challenge.code()
                + ". Expira en " + challenge.ttlMinutes() + " minutos.");
        mailSender.send(message);
    }

    private String subject(PurposeCode purposeCode) {
        return switch (purposeCode) {
            case LOGIN -> "Tu código de verificación";
            case EMAIL_CONFIRMATION -> "Confirma tu correo electrónico";
            case PASSWORD_RESET -> "Recuperación de contraseña";
        };
    }
}
