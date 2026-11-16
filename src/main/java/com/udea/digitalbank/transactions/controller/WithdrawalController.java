package com.udea.digitalbank.transactions.controller;

import com.udea.digitalbank.shared.config.OpenAPIConfig;
import com.udea.digitalbank.shared.config.OpenApiExamples;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.shared.security.CurrentActor;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.dto.WithdrawalRequest;
import com.udea.digitalbank.transactions.service.WithdrawalInitiationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de inicio del retiro (HU19), el primer paso de dos como en la transferencia. Comparte la ruta base de
 * transacciones, pero va en su propio controller, como el de consignaciones, porque su cuerpo es otro. El segundo paso
 * no tiene ruta propia: se confirma con {@code POST /api/v1/transactions/{reference}/confirm}, como una transferencia
 * (ADR-0006), y el comprobante se consulta con {@code GET /api/v1/transactions/{reference}}.
 *
 * <p>Qué rol entra a cada ruta lo decide {@code TransactionSecurity} en la cadena de seguridad, y qué puede hacer cada
 * uno lo deciden los servicios: aquí solo se traduce HTTP.
 */
@Tag(name = OpenAPIConfig.TAG_TRANSACTIONS)
@RestController
@RequestMapping("/api/v1/transactions")
public class WithdrawalController {

    private final WithdrawalInitiationService initiationService;

    public WithdrawalController(WithdrawalInitiationService initiationService) {
        this.initiationService = initiationService;
    }

    /**
     * Inicia un retiro ({@code POST /api/v1/transactions/withdrawals}): lo deja pendiente y envía el código de segundo
     * factor al correo del titular de la cuenta.
     *
     * <p>Si lo inicia un administrador, el código igual va al titular, y el administrador lo ingresa al confirmar.
     *
     * @param actor   usuario autenticado; es el ordenante
     * @param request cuenta y monto
     * @return 201 con la referencia y el instante en que vence el código
     */
    @Operation(summary = "Iniciar un retiro")
    @ApiResponse(responseCode = "201",
            description = "Retiro creado en estado PENDING; el código se envía al correo del titular")
    @ApiResponse(responseCode = "400", description = "Solicitud inválida: monto igual o menor que cero o con más de "
            + "dos decimales, o número de cuenta mal formado")
    @ApiResponse(responseCode = "403", description = "Sin permiso, o la cuenta no está activa")
    @ApiResponse(responseCode = "404", description = "La cuenta no existe o no es del ordenante")
    @ApiResponse(responseCode = "409",
            description = "Saldo insuficiente, o el monto supera un tope diario (solo clientes)")
    @PostMapping("/withdrawals")
    public ResponseEntity<TransactionResponse> withdraw(@CurrentActor Actor actor,
                                                        @Valid @RequestBody WithdrawalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(initiationService.initiate(actor, request.getAccountNumber(), request.getAmount()));
    }
}
