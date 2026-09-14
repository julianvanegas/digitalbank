package com.udea.digitalbank.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PasswordResetRequest {
    @NotBlank
    private String token;

    @NotBlank
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#_-]).{8,16}$",
            message = "La contraseña debe tener 8-16 caracteres, mayúscula, minúscula, número y carácter especial"
    )
    private String password;
}
