package com.udea.digitalbank.customer.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

// El rol no viaja en el request: lo fija el endpoint
@Data
public class CreateCustomerRequest {
    // Perfil
    @NotBlank @Size(max = 255)
    private String name;

    @NotNull
    private Short documentTypeId;

    @NotBlank @Size(max = 255)
    private String documentNumber;

    @NotBlank @Size(max = 30)
    private String phone;

    @NotNull
    private LocalDate birthDate;   // "mayor de 18" se valida en el service, no aquí

    // Cuenta
    @NotBlank @Email @Size(max = 254)
    private String email;

    @NotBlank
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#_-]).{8,16}$",
            message = "La contraseña debe tener 8-16 caracteres, mayúscula, minúscula, número y carácter especial"
    )
    private String password;
}
