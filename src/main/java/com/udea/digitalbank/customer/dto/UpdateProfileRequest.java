package com.udea.digitalbank.customer.dto;

import com.udea.digitalbank.shared.utils.Phone;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

// El email no se edita aquí: pertenece a users
@Data
public class UpdateProfileRequest {
    // Todos los campos son opcionales, pero uno presente no puede estar en blanco
    @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "no puede estar en blanco")
    private String firstNames;

    @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "no puede estar en blanco")
    private String lastNames;

    @Phone
    private String phone;
}
