package com.udea.digitalbank.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

@Data
@AllArgsConstructor
public class LoginResponse {
    @Schema(example = "true")
    private boolean requiresTwoFactor;
    @Schema(example = OpenApiExamples.CHALLENGE_ID)
    private UUID challengeId;      // se devuelve junto con el código en /verify-2fa
}
