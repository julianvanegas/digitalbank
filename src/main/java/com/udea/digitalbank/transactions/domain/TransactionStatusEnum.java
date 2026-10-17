package com.udea.digitalbank.transactions.domain;

// Contrato del código con la tabla status_transaction: TransactionCatalog comprueba al arrancar que coinciden
public enum TransactionStatusEnum {
    PENDING, COMPLETED, REJECTED, REVERSED
}
