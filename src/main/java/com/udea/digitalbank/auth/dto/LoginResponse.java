package com.udea.digitalbank.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class LoginResponse {
    private boolean requiresTwoFactor;
    private UUID challengeId;      // se devuelve junto con el código en /verify-2fa
}
