package com.udea.digitalbank.shared.exception;

/**
 * Se pidió algo demasiado pronto o demasiadas veces. Si tiene sentido reintentar, indica en cuántos
 * segundos (el manejador lo traduce a la cabecera Retry-After); con 0 no hay nada que esperar.
 */
public class TooManyRequestsException extends BusinessException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(Kind.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
