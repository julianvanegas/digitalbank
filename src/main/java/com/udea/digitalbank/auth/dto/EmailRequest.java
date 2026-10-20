package com.udea.digitalbank.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

/** Para los endpoints públicos que solo necesitan un email (reenviar confirmación, recuperar contraseña). */
@Data
public class EmailRequest {
    @Schema(example = OpenApiExamples.EMAIL)
    @NotBlank @Email @Size(max = 254)
    private String email;
}
