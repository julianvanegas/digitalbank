package com.udea.digitalbank.customer.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

// El email no se edita aquí: pertenece a user_account
@Data
public class UpdateProfileRequest {
    @Size(max = 255)
    private String name;

    @Size(max = 30)
    private String phone;
}
