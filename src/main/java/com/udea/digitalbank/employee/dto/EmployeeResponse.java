package com.udea.digitalbank.employee.dto;

import lombok.Data;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;
import com.udea.digitalbank.auth.api.UserStatusEnum;

@Data
public class EmployeeResponse {
    @Schema(example = OpenApiExamples.EMPLOYEE_ID)
    private UUID id;
    @Schema(example = OpenApiExamples.EMPLOYEE_FIRST_NAMES)
    private String firstNames;
    @Schema(example = OpenApiExamples.EMPLOYEE_LAST_NAMES)
    private String lastNames;
    @Schema(example = OpenApiExamples.EMPLOYEE_USER_ID)
    private UUID userId;
    @Schema(example = OpenApiExamples.EMPLOYEE_EMAIL)
    private String email;
    @Schema(allowableValues = {"ADMIN"}, example = "ADMIN")
    private String role;
    @Schema(implementation = UserStatusEnum.class, example = "ACTIVE")
    private String status;
}
