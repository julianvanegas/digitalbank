package com.udea.digitalbank.accounts.domain;

// Acciones que quedan en el registro de acciones de una cuenta; el CHECK de account_actions admite exactamente estos valores
public enum AccountActionType {
    BLOCKED, UNLOCKED, CLOSED
}
