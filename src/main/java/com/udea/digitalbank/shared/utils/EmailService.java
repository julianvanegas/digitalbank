package com.udea.digitalbank.shared.utils;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

/**
 * Envío de correo de texto plano para cualquier módulo. Solo se ocupa del transporte: el asunto y
 * el contenido los decide quien lo usa. El envío ocurre en segundo plano para que un fallo del
 * servidor de correo no interrumpa el flujo principal de la petición.
 */
@Service
public class EmailService {

    private final Resend resend;
    private final String from;

    public EmailService(@Value("${resend.api-key}") String apiKey,
            @Value("${resend.from}") String from) {
        this.resend = new Resend(apiKey);
        this.from = from;
    }

    @Async
    public void send(String to, String subject, String text) {
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(from)
                .to(to)
                .subject(subject)
                .text(text)
                .build();

        try {
            resend.emails().send(params);
        } catch (ResendException exception) {
            throw new IllegalStateException("No se pudo enviar el correo mediante Resend", exception);
        }
    }
}
