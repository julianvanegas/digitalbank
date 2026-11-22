package com.udea.digitalbank.transactions.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.udea.digitalbank.accounts.api.AccountTypeEnum;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Topes vigentes de un tipo de movimiento para un tipo de cuenta. Un tope que no aplica no se incluye en la respuesta:
 * las transferencias, por ejemplo, solo informan el acumulado diario.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionLimitResponse {

    /** Tipo de movimiento al que aplican los topes. */
    @Schema(implementation = TransactionTypeEnum.class, example = "WITHDRAWAL")
    private String type;

    /** Tipo de cuenta de la que sale el dinero. */
    @Schema(implementation = AccountTypeEnum.class, example = "SAVINGS")
    private String accountType;

    /** Moneda de los montos. */
    @Schema(example = "COP")
    private String currency;

    /** Cantidad máxima de operaciones al día por cuenta. */
    @Schema(example = "4")
    private Integer maxDailyCount;

    /** Monto máximo de una sola operación. */
    @Schema(example = "2500000.00")
    private BigDecimal maxPerTransaction;

    /** Monto máximo acumulado al día por cuenta. */
    @Schema(example = "10000000.00")
    private BigDecimal maxDailyAmount;

    /** Siempre {@code true}: toda operación monetaria exige verificación con segundo factor. */
    @Schema(example = "true")
    private boolean requiresTwoFactor;
}
