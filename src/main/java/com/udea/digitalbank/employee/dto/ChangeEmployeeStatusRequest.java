package com.udea.digitalbank.employee.dto;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
public class ChangeEmployeeStatusRequest {
    @Schema(allowableValues = {"ACTIVE", "INACTIVE", "BLOCKED"}, example = "INACTIVE")
    @NotNull
    private UserStatusEnum status;
}
