package com.udea.digitalbank.auth.api;

/**
 * Resultado de verificar un código de segundo factor. Lleva los intentos que quedan solo cuando el código fue
 * incorrecto pero sigue vigente; en los demás resultados vale 0.
 */
public record CodeCheckResult(Outcome outcome, int remainingAttempts) {

    public enum Outcome {
        /** El código es correcto y vigente: queda consumido. */
        VALID,
        /** Incorrecto, con intentos disponibles: el código sigue vigente. */
        WRONG,
        /** Incorrecto y era el último intento: el código quedó invalidado. */
        EXHAUSTED,
        /** Venció o ya no existe: hay que emitir uno nuevo. */
        EXPIRED
    }

    public static CodeCheckResult valid() {
        return new CodeCheckResult(Outcome.VALID, 0);
    }

    public static CodeCheckResult wrong(int remainingAttempts) {
        return new CodeCheckResult(Outcome.WRONG, remainingAttempts);
    }

    public static CodeCheckResult exhausted() {
        return new CodeCheckResult(Outcome.EXHAUSTED, 0);
    }

    public static CodeCheckResult expired() {
        return new CodeCheckResult(Outcome.EXPIRED, 0);
    }
}
