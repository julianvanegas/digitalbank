package com.udea.digitalbank.customer.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;
import com.udea.digitalbank.auth.api.UserStatusEnum;

@Data
public class CustomerResponse {
    @Schema(example = OpenApiExamples.CUSTOMER_ID)
    private UUID id;
    @Schema(example = OpenApiExamples.FIRST_NAMES)
    private String firstNames;
    @Schema(example = OpenApiExamples.LAST_NAMES)
    private String lastNames;
    private DocumentTypeResponse documentType;
    @Schema(example = OpenApiExamples.DOCUMENT_NUMBER)
    private String documentNumber;
    @Schema(example = OpenApiExamples.PHONE)
    private String phone;
    @Schema(example = OpenApiExamples.BIRTH_DATE)
    private LocalDate birthDate;
    @Schema(example = OpenApiExamples.USER_ID)
    private UUID userId;
    @Schema(example = OpenApiExamples.EMAIL)
    private String email;
    @Schema(allowableValues = {"CUSTOMER"}, example = "CUSTOMER")
    private String role;
    @Schema(implementation = UserStatusEnum.class, example = "ACTIVE")
    private String status;
}
