package com.udea.digitalbank.identity.dto;

import com.udea.digitalbank.identity.domain.CustomerStatus;
import com.udea.digitalbank.identity.domain.Role;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CustomerResponse {
    private Long id;
    private String name;
    private String documentType;
    private String documentNumber;
    private String email;
    private String phone;
    private LocalDate birthDate;
    private Role role;
    private CustomerStatus status;
}
