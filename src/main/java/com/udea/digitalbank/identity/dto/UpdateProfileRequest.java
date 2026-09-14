package com.udea.digitalbank.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(max = 100)
    private String name;

    @Size(max = 30)
    private String phone;

    @Email
    @Size(max = 254)
    private String email;
}
