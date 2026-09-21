package com.udea.digitalbank.shared.utils;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envío de correo de texto plano para cualquier módulo. Solo se ocupa del transporte: el asunto y
 * el contenido los decide quien lo usa. Un fallo del servidor de correo sube como MailException
 * y cada llamador decide si debe deshacer su operación o continuar.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
