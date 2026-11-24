package com.udea.digitalbank.transactions.controller;

import com.udea.digitalbank.shared.config.OpenAPIConfig;
import com.udea.digitalbank.shared.config.OpenApiExamples;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.shared.security.CurrentActor;
import com.udea.digitalbank.transactions.dto.TransactionHistoryEntry;
import com.udea.digitalbank.transactions.service.TransactionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Endpoint del historial de movimientos de una cuenta. Comparte la ruta base de transacciones, pero va en su propio
 * controller, como el de consignaciones y el de retiros, porque es otra consulta.
 *
 * <p>Qué rol entra a la ruta lo decide {@code TransactionSecurity} en la cadena de seguridad, y qué cuentas puede
 * consultar cada uno lo decide el servicio: aquí solo se traduce HTTP.
 */
@Tag(name = OpenAPIConfig.TAG_TRANSACTIONS)
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionHistoryController {

    private final TransactionHistoryService historyService;

    public TransactionHistoryController(TransactionHistoryService historyService) {
        this.historyService = historyService;
    }

    /**
     * Consulta el historial de una cuenta en un rango de fechas
     * ({@code GET /api/v1/transactions?accountNumber=...&from=...&to=...}). Un cliente consulta sus propias cuentas (una
     * ajena responde igual que una inexistente) y un administrador la de cualquier cliente.
     *
     * <p>Cada movimiento se ve desde la cuenta: positivo si el dinero entró y negativo si salió. Solo se listan los
     * completados, del más reciente al más antiguo, y ambas fechas están incluidas.
     *
     * @param actor         usuario autenticado
     * @param accountNumber número de la cuenta (12 dígitos)
     * @param from          primer día del rango, en formato {@code yyyy-MM-dd}
     * @param to            último día del rango, en formato {@code yyyy-MM-dd}
     * @param pageable      página y tamaño; el tamaño máximo es 100
     * @return 200 con una página de movimientos
     */
    @Operation(summary = "Consultar el historial de transacciones de una cuenta")
    @ApiResponse(responseCode = "200", description = "Página de movimientos: positivos los ingresos, negativos los egresos")
    @ApiResponse(responseCode = "400",
            description = "Falta un parámetro, una fecha no tiene el formato yyyy-MM-dd, o la fecha inicial es "
                + "posterior a la final")
    @ApiResponse(responseCode = "404", description = "La cuenta no existe o no es del usuario")
    @GetMapping
    public ResponseEntity<Page<TransactionHistoryEntry>> history(
            @CurrentActor Actor actor,
            @Parameter(description = "Número de la cuenta (12 dígitos)",
                    example = OpenApiExamples.ACCOUNT_NUMBER,
                    schema = @Schema(pattern = "\\d{12}")) @RequestParam String accountNumber,
            @Parameter(description = "Primer día del rango, incluido (yyyy-MM-dd)",
                    example = OpenApiExamples.DATE_FROM)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Último día del rango, incluido (yyyy-MM-dd)",
                    example = OpenApiExamples.DATE_TO)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(historyService.getHistory(actor, accountNumber, from, to, pageable));
    }
}
