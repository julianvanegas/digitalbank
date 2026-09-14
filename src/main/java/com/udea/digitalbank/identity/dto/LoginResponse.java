package com.udea.digitalbank.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private boolean requiresTwoFactor;
    private Long customerId;
}
