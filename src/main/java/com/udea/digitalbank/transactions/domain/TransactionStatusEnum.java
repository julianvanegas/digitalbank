package com.udea.digitalbank.transactions.domain;

/**
 * Estados de una transacción. Deben coincidir con la tabla {@code status_transaction}; {@code TransactionCatalog}
 * lo comprueba al arrancar y la aplicación no inicia si difieren.
 */
public enum TransactionStatusEnum {
    /** Creada y a la espera del segundo factor; todavía no se ha movido dinero. */
    PENDING,
    /** El segundo factor está verificado y el dinero se ha movido. */
    COMPLETED,
    /** Cancelada sin mover dinero: código agotado o vencido, reemplazada por otra o inválida al confirmar. */
    REJECTED,
    /** Revertida mediante una operación compensatoria. Reservado: ningún flujo lo asigna. */
    REVERSED
}
