package com.udea.digitalbank.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class VerifyTwoFactorRequest {
    @NotNull
    private UUID challengeId;

    @NotBlank
    private String code;
}
