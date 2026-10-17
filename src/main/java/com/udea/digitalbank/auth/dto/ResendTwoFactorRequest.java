package com.udea.digitalbank.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ResendTwoFactorRequest {
    @NotNull
    private UUID challengeId;     // el que devuelve /login: no cambia entre reenvíos
}
