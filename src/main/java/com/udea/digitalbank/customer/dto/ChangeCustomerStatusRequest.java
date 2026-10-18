package com.udea.digitalbank.customer.dto;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

/**
 * El cliente se identifica por tipo y número de documento (el UNIQUE de customers), en el cuerpo
 * y no en la URL para que el documento no quede en logs ni en el historial de los clientes HTTP.
 */
@Data
public class ChangeCustomerStatusRequest {
    @Schema(example = OpenApiExamples.DOCUMENT_TYPE_ID)
    @NotNull
    private Short documentTypeId;

    @Schema(example = OpenApiExamples.DOCUMENT_NUMBER)
    @NotBlank @Size(max = 20)
    private String documentNumber;

    @Schema(allowableValues = {"ACTIVE", "INACTIVE", "BLOCKED"}, example = "INACTIVE")
    @NotNull
    private UserStatusEnum status;
}
