package com.udea.digitalbank.transactions.controller;

import com.udea.digitalbank.shared.config.OpenAPIConfig;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.shared.security.CurrentActor;
import com.udea.digitalbank.transactions.dto.DepositRequest;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.service.DepositService;
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
 * Endpoint de consignaciones. Comparte la ruta base de transacciones, pero va en su propio controller porque lo usa
 * otro rol (el administrador) y tiene otro flujo: un solo paso, sin segundo factor.
 *
 * <p>Que solo un administrador pueda usarlo lo decide {@code TransactionSecurity} en la cadena de seguridad, antes de
 * que la petición llegue aquí.
 */
@Tag(name = OpenAPIConfig.TAG_TRANSACTIONS)
@RestController
@RequestMapping("/api/v1/transactions")
public class DepositController {

    private final DepositService depositService;

    public DepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    /**
     * Registra una consignación ({@code POST /api/v1/transactions/deposits}): acredita el monto a la cuenta de destino
     * y devuelve el comprobante.
     *
     * @param actor   administrador autenticado que registra el depósito
     * @param request cuenta de destino y monto
     * @return 201 con el comprobante del depósito completado
     */
    @Operation(summary = "Realizar una consignación (administrador)")
    @ApiResponse(responseCode = "201", description = "Consignación completada; el saldo ya está actualizado")
    @ApiResponse(responseCode = "400",
            description = "Monto igual o menor que cero, solicitud inválida, cuenta de destino inexistente, o cuenta "
                + "bloqueada, cerrada o inactiva")
    @ApiResponse(responseCode = "403", description = "Sin permiso: solo un administrador puede consignar")
    @PostMapping("/deposits")
    public ResponseEntity<TransactionResponse> deposit(@CurrentActor Actor actor,
                                                       @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(depositService.deposit(actor, request));
    }
}
