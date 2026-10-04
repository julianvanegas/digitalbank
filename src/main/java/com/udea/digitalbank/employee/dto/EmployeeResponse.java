package com.udea.digitalbank.employee.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class EmployeeResponse {
    private UUID id;
    private String firstNames;
    private String lastNames;
    private UUID userId;
    private String email;
    private String role;
    private String status;
}
