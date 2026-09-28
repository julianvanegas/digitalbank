package com.udea.digitalbank.customer.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CustomerResponse {
    private Long id;
    private String firstNames;
    private String lastNames;
    private DocumentTypeResponse documentType;
    private String documentNumber;
    private String phone;
    private LocalDate birthDate;
    private UUID userId;
    private String email;
    private String role;
    private String status;
}
