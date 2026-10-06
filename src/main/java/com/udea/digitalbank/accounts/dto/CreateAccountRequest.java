package com.udea.digitalbank.accounts.dto;

import lombok.Data;

import java.util.UUID;

/**
 * El cuerpo es opcional: un cliente abre su propia cuenta sin enviar nada y el titular se toma del
 * token. Solo un administrador que abre la cuenta en nombre de un cliente indica aquí su customerId.
 */
@Data
public class CreateAccountRequest {
    private UUID customerId;
}
