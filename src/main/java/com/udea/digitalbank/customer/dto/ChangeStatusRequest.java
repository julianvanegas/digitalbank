package com.udea.digitalbank.customer.dto;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeStatusRequest {
    @NotNull
    private UserStatusEnum status;
}
