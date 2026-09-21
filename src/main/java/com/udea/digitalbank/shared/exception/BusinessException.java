package com.udea.digitalbank.shared.exception;

/**
 * Base de las excepciones de negocio. Declara qué clase de problema es, en términos del dominio, sin
 * mencionar HTTP: GlobalExceptionHandler traduce cada categoría a su código en un solo lugar, así que
 * una excepción nueva es solo una clase y no obliga a tocar el manejador.
 */
public abstract class BusinessException extends RuntimeException {

    public enum Kind {
        /** El recurso pedido no existe. */
        NOT_FOUND,
        /** La operación choca con algo que ya existe. */
        CONFLICT,
        /** Las credenciales o el código presentados no son válidos. */
        UNAUTHENTICATED,
        /** Se sabe quién es, pero no puede hacer esto ahora. */
        FORBIDDEN,
        /** El dato recibido no es válido. */
        INVALID
    }

    private final Kind kind;

    protected BusinessException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
