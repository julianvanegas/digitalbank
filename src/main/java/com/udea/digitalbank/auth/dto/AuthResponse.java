package com.udea.digitalbank.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

@Data
@AllArgsConstructor
public class AuthResponse {
    @Schema(example = OpenApiExamples.TOKEN)
    private String token;
}
