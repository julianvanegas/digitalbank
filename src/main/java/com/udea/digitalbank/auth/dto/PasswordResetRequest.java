package com.udea.digitalbank.auth.dto;

import com.udea.digitalbank.shared.utils.Password;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordResetRequest {
    @NotBlank @Email
    private String email;

    @NotBlank
    private String code;

    @NotBlank @Password
    private String password;
}
