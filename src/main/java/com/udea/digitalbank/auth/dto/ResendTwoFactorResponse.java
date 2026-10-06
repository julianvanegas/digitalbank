package com.udea.digitalbank.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class ResendTwoFactorResponse {
    private UUID challengeId;          // el mismo de /login: se sigue usando en /verify-2fa
    private int remainingResends;      // reenvíos que quedan antes de tener que iniciar sesión otra vez
    private long retryAfterSeconds;    // espera mínima hasta el próximo reenvío
}
