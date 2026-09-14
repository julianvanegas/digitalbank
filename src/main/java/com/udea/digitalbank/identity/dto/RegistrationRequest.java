package com.udea.digitalbank.identity.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class RegistrationRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String documentType;

    @NotBlank
    private String documentNumber;

    @NotBlank @Email
    private String email;

    @NotBlank
    private String phone;

    @NotNull
    private LocalDate birthDate;   // "mayor de 18" se valida en el service, no aquí

    @NotBlank
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#_-]).{8,16}$",
            message = "La contraseña debe tener 8-16 caracteres, mayúscula, minúscula, número y carácter especial"
    )
    private String password;
}
