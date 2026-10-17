package com.udea.digitalbank.auth.api;

/**
 * Resultado de verificar un código de segundo factor.
 *
 * @param outcome           qué ocurre con el código
 * @param remainingAttempts intentos que quedan; solo tiene valor cuando el código es incorrecto pero sigue vigente
 *                          ({@link Outcome#WRONG}), y vale 0 en los demás resultados
 */
public record CodeCheckResult(Outcome outcome, int remainingAttempts) {

    public enum Outcome {
        /** Código correcto y vigente; queda consumido. */
        VALID,
        /** Código incorrecto con intentos disponibles; sigue vigente. */
        WRONG,
        /** Código incorrecto en el último intento; queda invalidado. */
        EXHAUSTED,
        /** El código ha vencido o ya no existe; se debe emitir uno nuevo. */
        EXPIRED
    }

    /** @return resultado de un código correcto */
    public static CodeCheckResult valid() {
        return new CodeCheckResult(Outcome.VALID, 0);
    }

    /**
     * @param remainingAttempts intentos que quedan
     * @return resultado de un código incorrecto que sigue vigente
     */
    public static CodeCheckResult wrong(int remainingAttempts) {
        return new CodeCheckResult(Outcome.WRONG, remainingAttempts);
    }

    /** @return resultado de un código incorrecto en el último intento */
    public static CodeCheckResult exhausted() {
        return new CodeCheckResult(Outcome.EXHAUSTED, 0);
    }

    /** @return resultado de un código vencido o inexistente */
    public static CodeCheckResult expired() {
        return new CodeCheckResult(Outcome.EXPIRED, 0);
    }
}
