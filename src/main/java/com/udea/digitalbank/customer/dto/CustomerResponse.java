package com.udea.digitalbank.customer.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CustomerResponse {
    private Long id;
    private String name;
    private DocumentTypeResponse documentType;
    private String documentNumber;
    private String phone;
    private LocalDate birthDate;
    private Long userAccountId;
    private String email;
    private String role;
    private String status;
}
