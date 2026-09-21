package com.udea.digitalbank.auth.domain;

// Contrato del código con la tabla correspondiente: Catalogs comprueba al arrancar que coinciden
public enum StatusCode {
    ACTIVE, PENDING_VERIFICATION, INACTIVE, BLOCKED, TERMINATED
}
