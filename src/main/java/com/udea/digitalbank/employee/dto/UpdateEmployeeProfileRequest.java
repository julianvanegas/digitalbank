package com.udea.digitalbank.employee.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

/** El email no se edita aquí: pertenece a users. */
@Data
public class UpdateEmployeeProfileRequest {
    /**
     * Al menos un carácter que no sea espacio y ningún salto de línea. Los espacios iniciales y el resto se separan en
     * clases disjuntas, y el tramo final es posesivo, para que no haya backtracking.
     */
    private static final String NAME_PATTERN =
            "[^\\S\\r\\n\\u0085\\u2028\\u2029]*\\S[^\\r\\n\\u0085\\u2028\\u2029]*+";

    /** Todos los campos son opcionales, pero uno presente no puede estar en blanco. */
    @Schema(example = OpenApiExamples.EMPLOYEE_FIRST_NAMES)
    @Size(max = 100) @Pattern(regexp = NAME_PATTERN, message = "no puede estar en blanco ni tener saltos de línea")
    private String firstNames;

    @Schema(example = OpenApiExamples.EMPLOYEE_LAST_NAMES)
    @Size(max = 100) @Pattern(regexp = NAME_PATTERN, message = "no puede estar en blanco ni tener saltos de línea")
    private String lastNames;
}
