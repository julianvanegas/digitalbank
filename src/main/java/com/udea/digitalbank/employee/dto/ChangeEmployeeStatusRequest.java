package com.udea.digitalbank.employee.dto;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeEmployeeStatusRequest {
    @NotNull
    private UserStatusEnum status;
}
