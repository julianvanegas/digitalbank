package com.udea.digitalbank.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    private String email;      // o documentNumber, según cómo definas el login

    @NotBlank
    private String password;
}
