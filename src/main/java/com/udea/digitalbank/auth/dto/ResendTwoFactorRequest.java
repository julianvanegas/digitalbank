package com.udea.digitalbank.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

@Data
public class ResendTwoFactorRequest {
    @Schema(example = OpenApiExamples.CHALLENGE_ID)
    @NotNull
    private UUID challengeId;     // el que devuelve /login: no cambia entre reenvíos
}
