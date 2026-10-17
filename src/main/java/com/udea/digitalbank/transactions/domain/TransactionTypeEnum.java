package com.udea.digitalbank.transactions.domain;

/**
 * Tipos de movimiento. Deben coincidir con la tabla {@code transaction_types}; {@code TransactionCatalog} lo
 * comprueba al arrancar y la aplicación no inicia si difieren. Solo {@code TRANSFER} tiene un flujo implementado.
 */
public enum TransactionTypeEnum {
    /** Entre dos cuentas: exige cuenta de origen y de destino. */
    TRANSFER,
    /** Ingreso de dinero: solo tiene cuenta de destino. */
    DEPOSIT,
    /** Retiro de dinero: solo tiene cuenta de origen. */
    WITHDRAWAL
}
