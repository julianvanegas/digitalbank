package com.udea.digitalbank.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Para los endpoints públicos que solo necesitan un email (reenviar confirmación, recuperar contraseña)
@Data
public class EmailRequest {
    @NotBlank @Email @Size(max = 254)
    private String email;
}
