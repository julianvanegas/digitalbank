package com.udea.digitalbank.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Sin contraseña ni rol: la contraseña la define el empleado al verificar su correo y el rol lo fija el endpoint
@Data
public class CreateEmployeeRequest {
    @NotBlank @Size(max = 100)
    private String firstNames;

    @NotBlank @Size(max = 100)
    private String lastNames;

    @NotBlank @Email @Size(max = 254)
    private String email;
}
