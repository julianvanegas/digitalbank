package com.udea.digitalbank.auth.api;

// Contrato del código con la tabla user_status: Catalogs comprueba al arrancar que coinciden
public enum UserStatusEnum {
    ACTIVE, PENDING_VERIFICATION, INACTIVE, BLOCKED
}
