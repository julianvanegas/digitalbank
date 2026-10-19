package com.udea.digitalbank.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

/** Sin contraseña ni rol: la contraseña la define el empleado al verificar su correo y el rol lo fija el endpoint. */
@Data
public class CreateEmployeeRequest {
    @Schema(example = OpenApiExamples.EMPLOYEE_FIRST_NAMES)
    @NotBlank @Size(max = 100)
    private String firstNames;

    @Schema(example = OpenApiExamples.EMPLOYEE_LAST_NAMES)
    @NotBlank @Size(max = 100)
    private String lastNames;

    @Schema(example = OpenApiExamples.EMPLOYEE_EMAIL)
    @NotBlank @Email @Size(max = 254)
    private String email;
}
