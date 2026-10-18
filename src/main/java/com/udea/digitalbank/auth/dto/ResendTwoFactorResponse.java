package com.udea.digitalbank.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

@Data
@AllArgsConstructor
public class ResendTwoFactorResponse {
    @Schema(example = OpenApiExamples.CHALLENGE_ID)
    private UUID challengeId;          // el mismo de /login: se sigue usando en /verify-2fa
    @Schema(example = OpenApiExamples.REMAINING_RESENDS)
    private int remainingResends;      // reenvíos que quedan antes de tener que iniciar sesión otra vez
    @Schema(example = OpenApiExamples.RETRY_AFTER_SECONDS)
    private long retryAfterSeconds;    // espera mínima hasta el próximo reenvío
}
