package com.udea.digitalbank.auth.dto;

import com.udea.digitalbank.shared.utils.Password;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

/** La persona define su contraseña al confirmar el correo; la confirmación se compara en el service. */
@Data
public class VerifyEmailRequest {
    @Schema(example = OpenApiExamples.EMAIL)
    @NotBlank @Email
    private String email;

    @Schema(example = OpenApiExamples.CODE)
    @NotBlank
    private String code;

    @Schema(example = OpenApiExamples.PASSWORD)
    @NotBlank @Password
    private String password;

    @Schema(example = OpenApiExamples.PASSWORD)
    @NotBlank
    private String confirmPassword;
}
