package com.udea.digitalbank.shared.exception;

import com.udea.digitalbank.shared.exception.auth.DuplicateUserException;
import com.udea.digitalbank.shared.exception.auth.InvalidCredentialsException;
import com.udea.digitalbank.shared.exception.auth.InvalidVerificationCodeException;
import com.udea.digitalbank.shared.exception.auth.UserNotEnabledException;
import com.udea.digitalbank.shared.exception.auth.UserNotFoundException;
import com.udea.digitalbank.shared.exception.customer.CustomerNotFoundException;
import com.udea.digitalbank.shared.exception.customer.DuplicateCustomerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pruebas unitarias (AAA) para GlobalExceptionHandler.
 * No usa MockMvc/contexto de Spring: el handler es una clase corriente y se prueban sus
 * métodos directamente con instancias reales de cada excepción.
 */
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Nested
    @DisplayName("handleBusiness - traduce cada Kind de BusinessException a su código HTTP")
    class HandleBusinessTests {

        @Test
        @DisplayName("NOT_FOUND -> 404")
        void deberiaTraducirNotFoundA404() {
            // Arrange
            CustomerNotFoundException ex = new CustomerNotFoundException("Cliente no encontrado");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleBusiness(ex);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().message()).isEqualTo("Cliente no encontrado");
        }

        @Test
        @DisplayName("CONFLICT -> 409 (documento duplicado)")
        void deberiaTraducirConflictA409() {
            DuplicateCustomerException ex = new DuplicateCustomerException("Documento duplicado");
            ResponseEntity<ErrorResponse> response = handler.handleBusiness(ex);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("CONFLICT -> 409 (email duplicado)")
        void deberiaTraducirConflictA409ParaEmail() {
            DuplicateUserException ex = new DuplicateUserException("Email duplicado");
            ResponseEntity<ErrorResponse> response = handler.handleBusiness(ex);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("UNAUTHENTICATED -> 401 (credenciales inválidas)")
        void deberiaTraducirUnauthenticatedA401() {
            InvalidCredentialsException ex = new InvalidCredentialsException("Email o contraseña incorrectos");
            ResponseEntity<ErrorResponse> response = handler.handleBusiness(ex);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("FORBIDDEN -> 403 (usuario no habilitado)")
        void deberiaTraducirForbiddenA403() {
            UserNotEnabledException ex = new UserNotEnabledException("Usuario bloqueado");
            ResponseEntity<ErrorResponse> response = handler.handleBusiness(ex);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("INVALID -> 400 (código de verificación inválido)")
        void deberiaTraducirInvalidA400() {
            InvalidVerificationCodeException ex = new InvalidVerificationCodeException("Código inválido");
            ResponseEntity<ErrorResponse> response = handler.handleBusiness(ex);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("NOT_FOUND -> 404 (usuario no encontrado)")
        void deberiaTraducirNotFoundA404ParaUsuario() {
            UserNotFoundException ex = new UserNotFoundException("Usuario no encontrado");
            ResponseEntity<ErrorResponse> response = handler.handleBusiness(ex);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("TOO_MANY_REQUESTS -> 429 con Retry-After cuando hay tiempo de espera")
        void deberiaTraducirTooManyRequestsA429ConRetryAfter() {
            TooManyRequestsException ex = new TooManyRequestsException("Espera 20 segundos", 20);
            ResponseEntity<ErrorResponse> response = handler.handleBusiness(ex);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("20");
            assertThat(response.getBody().message()).isEqualTo("Espera 20 segundos");
        }

        @Test
        @DisplayName("TOO_MANY_REQUESTS -> 429 sin Retry-After cuando no hay nada que esperar")
        void deberiaTraducirTooManyRequestsA429SinRetryAfter() {
            TooManyRequestsException ex = new TooManyRequestsException("Inicia sesión de nuevo", 0);
            ResponseEntity<ErrorResponse> response = handler.handleBusiness(ex);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
        }

        @Test
        @DisplayName("Las demás excepciones de negocio no llevan Retry-After")
        void lasDemasExcepcionesNoLlevanRetryAfter() {
            ResponseEntity<ErrorResponse> response = handler.handleBusiness(new UserNotFoundException("x"));
            assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
        }

        @Test
        @DisplayName("El cuerpo siempre incluye un timestamp")
        void elCuerpoSiempreIncluyeTimestamp() {
            ResponseEntity<ErrorResponse> response = handler.handleBusiness(new UserNotFoundException("x"));
            assertThat(response.getBody().timestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Otros handlers públicos")
    class OtherPublicHandlersTests {

        @Test
        @DisplayName("DataIntegrityViolationException -> 409 con mensaje genérico")
        void deberiaManejarViolacionDeIntegridad() {
            // Arrange
            DataIntegrityViolationException ex = new DataIntegrityViolationException("constraint violada");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleIntegrity(ex);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().message()).isEqualTo("Ya existe un registro con esos datos");
        }

        @Test
        @DisplayName("IllegalArgumentException -> 400 con el mensaje original")
        void deberiaManejarIllegalArgument() {
            // Arrange
            IllegalArgumentException ex = new IllegalArgumentException("El cliente debe ser mayor de 18 años");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(ex);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().message()).isEqualTo("El cliente debe ser mayor de 18 años");
        }

        @Test
        @DisplayName("AccessDeniedException -> 403 con el mensaje estándar de seguridad")
        void deberiaManejarAccessDenied() {
            // Arrange
            AccessDeniedException ex = new AccessDeniedException("no importa el mensaje original");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody().message()).isEqualTo(SecurityErrorHandler.FORBIDDEN_MESSAGE);
        }

        @Test
        @DisplayName("AuthenticationException -> 401 con el mensaje estándar de seguridad")
        void deberiaManejarAuthenticationException() {
            // Arrange
            BadCredentialsException ex = new BadCredentialsException("no importa el mensaje original");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleAuthentication(ex);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().message()).isEqualTo(SecurityErrorHandler.UNAUTHENTICATED_MESSAGE);
        }

        @Test
        @DisplayName("Excepción no prevista -> 500 sin exponer el detalle interno")
        void deberiaManejarExcepcionNoPrevista() {
            // Arrange
            RuntimeException ex = new RuntimeException("detalle interno sensible: NPE en línea 42");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleUnexpected(ex);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().message()).isEqualTo("Error interno del servidor");
            assertThat(response.getBody().message()).doesNotContain("NPE");
        }
    }

    @Nested
    @DisplayName("Overrides de ResponseEntityExceptionHandler (errores propios de MVC)")
    class MvcHandlersTests {

        // Método señuelo, solo para poder construir un MethodParameter real
        @SuppressWarnings("unused")
        private void dummyMethod(Object arg) {
        }

        private MethodParameter buildMethodParameter() throws NoSuchMethodException {
            return new MethodParameter(
                    MvcHandlersTests.class.getDeclaredMethod("dummyMethod", Object.class), 0);
        }

        @Test
        @DisplayName("Campos inválidos de un DTO -> 400 con 'campo: mensaje' por cada error")
        void deberiaManejarUnSoloErrorDeValidacion() throws NoSuchMethodException {
            // Arrange
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
            bindingResult.addError(new FieldError("request", "email", "no puede estar en blanco"));
            MethodArgumentNotValidException ex =
                    new MethodArgumentNotValidException(buildMethodParameter(), bindingResult);

            // Act
            ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                    ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ErrorResponse body = (ErrorResponse) response.getBody();
            assertThat(body.message()).isEqualTo("email: no puede estar en blanco");
        }

        @Test
        @DisplayName("Varios campos inválidos -> se unen con '; '")
        void deberiaUnirVariosErroresDeValidacion() throws NoSuchMethodException {
            // Arrange
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
            bindingResult.addError(new FieldError("request", "email", "no puede estar en blanco"));
            bindingResult.addError(new FieldError("request", "phone", "formato inválido"));
            MethodArgumentNotValidException ex =
                    new MethodArgumentNotValidException(buildMethodParameter(), bindingResult);

            // Act
            ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                    ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

            // Assert
            ErrorResponse body = (ErrorResponse) response.getBody();
            assertThat(body.message()).contains("email: no puede estar en blanco");
            assertThat(body.message()).contains("phone: formato inválido");
            assertThat(body.message()).contains("; ");
        }

        @Test
        @DisplayName("JSON mal formado -> 400 con mensaje genérico de solicitud mal formada")
        void deberiaManejarJsonMalFormado() {
            // Arrange
            HttpMessageNotReadableException ex =
                    new HttpMessageNotReadableException("JSON parse error", mock(HttpInputMessage.class));

            // Act
            ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(
                    ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ErrorResponse body = (ErrorResponse) response.getBody();
            assertThat(body.message()).isEqualTo("Solicitud mal formada: revisa el cuerpo y los parámetros");
        }

        @Test
        @DisplayName("Parámetro con tipo incorrecto (ej. /customers/abc) -> 400 con mensaje genérico")
        void deberiaManejarTypeMismatch() {
            // Arrange
            TypeMismatchException ex = new TypeMismatchException("abc", Long.class);

            // Act
            ResponseEntity<Object> response = handler.handleTypeMismatch(
                    ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ErrorResponse body = (ErrorResponse) response.getBody();
            assertThat(body.message()).isEqualTo("Solicitud mal formada: revisa el cuerpo y los parámetros");
        }

        @Test
        @DisplayName("404 genérico de MVC -> mensaje 'Recurso no encontrado'")
        void deberiaMapear404GenericoDeMvc() {
            ResponseEntity<Object> response = handler.handleExceptionInternal(
                    new RuntimeException("no importa"), null, new HttpHeaders(), HttpStatus.NOT_FOUND, mock(WebRequest.class));
            ErrorResponse body = (ErrorResponse) response.getBody();
            assertThat(body.message()).isEqualTo("Recurso no encontrado");
        }

        @Test
        @DisplayName("405 genérico de MVC -> mensaje de método no permitido")
        void deberiaMapear405GenericoDeMvc() {
            ResponseEntity<Object> response = handler.handleExceptionInternal(
                    new RuntimeException("no importa"), null, new HttpHeaders(), HttpStatus.METHOD_NOT_ALLOWED, mock(WebRequest.class));
            ErrorResponse body = (ErrorResponse) response.getBody();
            assertThat(body.message()).isEqualTo("Método HTTP no permitido para esta ruta");
        }

        @Test
        @DisplayName("406 genérico de MVC -> mensaje de formato no aceptable")
        void deberiaMapear406GenericoDeMvc() {
            ResponseEntity<Object> response = handler.handleExceptionInternal(
                    new RuntimeException("no importa"), null, new HttpHeaders(), HttpStatus.NOT_ACCEPTABLE, mock(WebRequest.class));
            ErrorResponse body = (ErrorResponse) response.getBody();
            assertThat(body.message()).isEqualTo("Formato de respuesta no aceptable");
        }

        @Test
        @DisplayName("415 genérico de MVC -> mensaje de tipo de contenido no soportado")
        void deberiaMapear415GenericoDeMvc() {
            ResponseEntity<Object> response = handler.handleExceptionInternal(
                    new RuntimeException("no importa"), null, new HttpHeaders(),
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, mock(WebRequest.class));
            ErrorResponse body = (ErrorResponse) response.getBody();
            assertThat(body.message()).isEqualTo("Tipo de contenido no soportado");
        }

        @Test
        @DisplayName("Cualquier otro código de MVC -> mensaje por defecto")
        void deberiaMapearCodigoDesconocidoDeMvcAMensajePorDefecto() {
            ResponseEntity<Object> response = handler.handleExceptionInternal(
                    new RuntimeException("no importa"), null, new HttpHeaders(),
                    HttpStatus.INTERNAL_SERVER_ERROR, mock(WebRequest.class));
            ErrorResponse body = (ErrorResponse) response.getBody();
            assertThat(body.message()).isEqualTo("No se pudo procesar la solicitud");
        }
    }
}