package com.udea.digitalbank.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Convierte cualquier excepción en un ErrorResponse. Extiende la base de Spring para que los errores
 * propios de MVC (ruta inexistente, método no permitido, tipo de contenido, parámetro faltante...)
 * salgan con el mismo formato y su código correcto, y no con el cuerpo por defecto de Spring.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String MALFORMED_REQUEST = "Solicitud mal formada: revisa el cuerpo y los parámetros";

    // Todas las excepciones de negocio: cada una declara su categoría y aquí se decide el código HTTP
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return build(statusOf(ex.getKind()), ex.getMessage());
    }

    // Exhaustivo: una categoría nueva no compila hasta que se decide qué código le corresponde
    private static HttpStatus statusOf(BusinessException.Kind kind) {
        return switch (kind) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case INVALID -> HttpStatus.BAD_REQUEST;
        };
    }

    // Carrera entre dos altas con el mismo email o documento: la restricción única de la base gana
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "Ya existe un registro con esos datos");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // @PreAuthorize lanza esta excepción desde dentro del controller: sin este handler la atraparía el general (500)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, SecurityErrorHandler.FORBIDDEN_MESSAGE);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, SecurityErrorHandler.UNAUTHENTICATED_MESSAGE);
    }

    // Cualquier error no previsto. Al cliente no se le cuenta nada del fallo: el detalle va solo al log.
    // Sin este handler, un 500 en un endpoint público llegaba al anónimo enmascarado como 403.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    // ---- errores propios de MVC: Spring decide el código, aquí se decide el cuerpo ----

    // captura los @NotBlank, @Email, @Pattern, etc. de los DTOs
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, headers, message);
    }

    // Cuerpo ausente o JSON mal formado
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return body(HttpStatus.BAD_REQUEST, headers, MALFORMED_REQUEST);
    }

    // Parámetro de ruta o de consulta de tipo incorrecto (p. ej. /api/v1/customers/abc)
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        return body(HttpStatus.BAD_REQUEST, headers, MALFORMED_REQUEST);
    }

    // Todo lo demás de MVC (404, 405, 406, 415, parámetro faltante...) con un mensaje genérico por código
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        return ResponseEntity.status(statusCode).headers(headers)
                .body(new ErrorResponse(statusCode.value(), messageFor(statusCode), LocalDateTime.now()));
    }

    private static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> MALFORMED_REQUEST;
            case 404 -> "Recurso no encontrado";
            case 405 -> "Método HTTP no permitido para esta ruta";
            case 406 -> "Formato de respuesta no aceptable";
            case 415 -> "Tipo de contenido no soportado";
            default -> "No se pudo procesar la solicitud";
        };
    }

    private ResponseEntity<Object> body(HttpStatus status, HttpHeaders headers, String message) {
        return ResponseEntity.status(status).headers(headers)
                .body(new ErrorResponse(status.value(), message, LocalDateTime.now()));
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), message, LocalDateTime.now()));
    }
}
