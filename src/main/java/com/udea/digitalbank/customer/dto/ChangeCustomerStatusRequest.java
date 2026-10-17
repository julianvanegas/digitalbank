package com.udea.digitalbank.customer.dto;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

// El cliente se identifica por tipo y número de documento (el UNIQUE de customers), en el cuerpo
// y no en la URL para que el documento no quede en logs ni en el historial de los clientes HTTP
@Data
public class ChangeCustomerStatusRequest {
    @NotNull
    private Short documentTypeId;

    @NotBlank @Size(max = 20)
    private String documentNumber;

    @NotNull
    private UserStatusEnum status;
}
