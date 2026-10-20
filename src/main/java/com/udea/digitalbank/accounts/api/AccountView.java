package com.udea.digitalbank.accounts.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Vista de solo lectura de una cuenta para otros módulos (por ejemplo {@code transactions}). Evita exponer la entidad
 * {@code Account}.
 *
 * @param id            identificador de la cuenta
 * @param accountKey    llave visible de la cuenta
 * @param accountNumber número público de la cuenta (12 dígitos)
 * @param customerId    titular de la cuenta
 * @param status        estado como código, por ejemplo {@code "ACTIVE"}
 * @param balance       saldo actual
 */
public record AccountView(UUID id, String accountKey, String accountNumber, UUID customerId,
                          String status, BigDecimal balance) {

    /**
     * @return {@code true} si la cuenta puede mover dinero, es decir, si está {@code ACTIVE}. Evita que cada módulo
     *         compare el texto del estado.
     */
    public boolean isActive() {
        return AccountStatusEnum.ACTIVE.name().equals(status);
    }
}
