package com.udea.digitalbank.auth.dto;

import com.udea.digitalbank.shared.utils.Password;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// La persona define su contraseña al confirmar el correo; la confirmación se compara en el service
@Data
public class VerifyEmailRequest {
    @NotBlank @Email
    private String email;

    @NotBlank
    private String code;

    @NotBlank @Password
    private String password;

    @NotBlank
    private String confirmPassword;
}
