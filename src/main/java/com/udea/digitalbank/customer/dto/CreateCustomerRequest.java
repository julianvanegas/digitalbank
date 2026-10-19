package com.udea.digitalbank.customer.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import com.udea.digitalbank.shared.utils.Phone;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

/** Sin contraseña ni rol: la contraseña la define el cliente al verificar su correo y el rol lo fija el endpoint. */
@Data
public class CreateCustomerRequest {
    /** Perfil. */
    @Schema(example = OpenApiExamples.FIRST_NAMES)
    @NotBlank @Size(max = 100)
    private String firstNames;

    @Schema(example = OpenApiExamples.LAST_NAMES)
    @NotBlank @Size(max = 100)
    private String lastNames;

    @Schema(example = OpenApiExamples.DOCUMENT_TYPE_ID)
    @NotNull
    private Short documentTypeId;

    @Schema(example = OpenApiExamples.DOCUMENT_NUMBER)
    @NotBlank @Size(max = 20)
    private String documentNumber;

    @Schema(example = OpenApiExamples.PHONE)
    @NotBlank @Phone
    private String phone;

    @Schema(example = OpenApiExamples.BIRTH_DATE)
    @NotNull
    private LocalDate birthDate;   // "mayor de 18" se valida en el service, no aquí

    /** Usuario. */
    @Schema(example = OpenApiExamples.EMAIL)
    @NotBlank @Email @Size(max = 254)
    private String email;
}
