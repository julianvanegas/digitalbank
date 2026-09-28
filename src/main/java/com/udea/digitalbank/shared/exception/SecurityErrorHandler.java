package com.udea.digitalbank.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Respuestas de la cadena de seguridad, que rechaza la petición antes de que llegue a un controller y por
 * eso no pasa por GlobalExceptionHandler. Usa el mismo formato (ErrorResponse) para que el cliente
 * reciba siempre el mismo cuerpo, y distingue 401 (no autenticado) de 403 (sin permiso).
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    public static final String UNAUTHENTICATED_MESSAGE = "Autenticación requerida: falta el token o no es válido";
    public static final String FORBIDDEN_MESSAGE = "No tienes permiso para realizar esta operación";

    // Sin token, token inválido o expirado, o sesión revocada
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        write(response, 401, UNAUTHENTICATED_MESSAGE);
    }

    // Autenticado, pero su rol no alcanza para esa ruta
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(response, 403, FORBIDDEN_MESSAGE);
    }

    private static void write(HttpServletResponse response, int status, String message) throws IOException {
        ErrorResponse body = new ErrorResponse(status, message, LocalDateTime.now());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(String.format("{\"status\":%d,\"message\":\"%s\",\"timestamp\":\"%s\"}",
                body.status(), escape(body.message()), body.timestamp()));
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
