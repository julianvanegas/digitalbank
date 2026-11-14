package com.udea.digitalbank.transactions.controller;

import com.udea.digitalbank.shared.config.OpenAPIConfig;
import com.udea.digitalbank.transactions.dto.TransactionLimitResponse;
import com.udea.digitalbank.transactions.service.TransactionLimitQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consulta de los topes transaccionales. Comparte la ruta base de transacciones, pero va en su propio controller
 * porque es una consulta de configuración y no una operación sobre una transacción.
 *
 * <p>Cualquier usuario autenticado puede consultarlos; eso lo decide {@code TransactionSecurity}. Los topes solo se
 * aplican a los clientes, no a los administradores.
 */
@Tag(name = OpenAPIConfig.TAG_TRANSACTIONS)
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionLimitController {

    private final TransactionLimitQueryService limitQueryService;

    public TransactionLimitController(TransactionLimitQueryService limitQueryService) {
        this.limitQueryService = limitQueryService;
    }

    /**
     * Consulta los topes vigentes ({@code GET /api/v1/transactions/limits}). Sin filtros devuelve los de cada tipo de
     * movimiento (retiros y transferencias) y de cada tipo de cuenta. No modifica ninguna regla.
     *
     * <p>Los filtros se reciben como texto y no como enum para que un valor desconocido responda 404 con el mensaje
     * de que no hay topes para ese tipo, y no el 400 genérico de una solicitud mal formada.
     *
     * @param type        tipo de movimiento a consultar; opcional
     * @param accountType tipo de cuenta a consultar; opcional
     * @return 200 con los topes de cada combinación pedida
     */
    @Operation(summary = "Consultar los topes transaccionales")
    @ApiResponse(responseCode = "200", description = "Topes vigentes")
    @ApiResponse(responseCode = "404",
            description = "No existen topes definidos para el tipo de operación o de cuenta consultado")
    @GetMapping("/limits")
    public ResponseEntity<List<TransactionLimitResponse>> limits(
            @Parameter(description = "Tipo de movimiento: TRANSFER o WITHDRAWAL. Sin indicarlo, devuelve todos",
                    example = "WITHDRAWAL") @RequestParam(required = false) String type,
            @Parameter(description = "Tipo de cuenta: SAVINGS o CHECKING. Sin indicarlo, devuelve todos",
                    example = "SAVINGS") @RequestParam(required = false) String accountType) {
        return ResponseEntity.ok(limitQueryService.getLimits(type, accountType));
    }
}
