package com.udea.digitalbank.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

@Data
public class LoginRequest {
    @Schema(example = OpenApiExamples.EMAIL)
    @NotBlank
    private String email;      // o documentNumber, según cómo definas el login

    @Schema(example = OpenApiExamples.PASSWORD)
    @NotBlank
    private String password;
}
