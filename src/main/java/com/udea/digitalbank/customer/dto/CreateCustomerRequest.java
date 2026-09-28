package com.udea.digitalbank.customer.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import com.udea.digitalbank.shared.utils.Password;
import com.udea.digitalbank.shared.utils.Phone;
import java.time.LocalDate;

// El rol no viaja en el request: lo fija el endpoint
@Data
public class CreateCustomerRequest {
    // Perfil
    @NotBlank @Size(max = 100)
    private String firstNames;

    @NotBlank @Size(max = 100)
    private String lastNames;

    @NotNull
    private Short documentTypeId;

    @NotBlank @Size(max = 255)
    private String documentNumber;

    @NotBlank @Phone
    private String phone;

    @NotNull
    private LocalDate birthDate;   // "mayor de 18" se valida en el service, no aquí

    // Usuario
    @NotBlank @Email @Size(max = 254)
    private String email;

    @NotBlank @Password
    private String password;
}
