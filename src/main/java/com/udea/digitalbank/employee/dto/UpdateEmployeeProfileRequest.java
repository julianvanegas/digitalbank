package com.udea.digitalbank.employee.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

/** El email no se edita aquí: pertenece a users. */
@Data
public class UpdateEmployeeProfileRequest {
    /** Todos los campos son opcionales, pero uno presente no puede estar en blanco. */
    @Schema(example = OpenApiExamples.EMPLOYEE_FIRST_NAMES)
    @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "no puede estar en blanco")
    private String firstNames;

    @Schema(example = OpenApiExamples.EMPLOYEE_LAST_NAMES)
    @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "no puede estar en blanco")
    private String lastNames;
}
