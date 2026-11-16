package com.udea.digitalbank.transactions.controller;

import com.udea.digitalbank.shared.config.OpenAPIConfig;
import com.udea.digitalbank.shared.config.OpenApiExamples;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.shared.security.CurrentActor;
import com.udea.digitalbank.transactions.dto.ConfirmWithdrawalRequest;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.dto.WithdrawalRequest;
import com.udea.digitalbank.transactions.service.WithdrawalConfirmationService;
import com.udea.digitalbank.transactions.service.WithdrawalInitiationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints del retiro (HU19), en dos pasos como la transferencia: iniciar y confirmar con el código. Comparte la ruta
 * base de transacciones, pero va en su propio controller, como el de consignaciones, porque es otro flujo. El
 * comprobante de un retiro se consulta con {@code GET /api/v1/transactions/{reference}}, como el de cualquier
 * transacción.
 *
 * <p>Qué rol entra a cada ruta lo decide {@code TransactionSecurity} en la cadena de seguridad, y qué puede hacer cada
 * uno lo deciden los servicios: aquí solo se traduce HTTP.
 */
@Tag(name = OpenAPIConfig.TAG_TRANSACTIONS)
@RestController
@RequestMapping("/api/v1/transactions")
public class WithdrawalController {

    private final WithdrawalInitiationService initiationService;
    private final WithdrawalConfirmationService confirmationService;

    public WithdrawalController(WithdrawalInitiationService initiationService,
                                WithdrawalConfirmationService confirmationService) {
        this.initiationService = initiationService;
        this.confirmationService = confirmationService;
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

    /**
     * Confirma un retiro con su código ({@code POST /api/v1/transactions/withdrawals/{reference}/confirm}): con el
     * código correcto debita la cuenta y devuelve el comprobante.
     *
     * <p>Respuestas de error: 400 si el código es incorrecto (el retiro sigue pendiente y se informan los intentos
     * restantes); 409 si el código ha vencido y 429 si se han agotado los intentos (en ambos casos el retiro queda
     * cancelado y se debe iniciar uno nuevo).
     *
     * <p>Si lo inició un administrador, lo confirma él con el código que recibió el titular de la cuenta.
     *
     * @param actor     usuario autenticado; debe ser el ordenante
     * @param reference comprobante del retiro
     * @param request   código de 6 dígitos recibido por correo
     * @return 200 con el comprobante del retiro completado
     */
    @Operation(summary = "Confirmar un retiro")
    @ApiResponse(responseCode = "200", description = "Retiro completado; la cuenta ya está debitada")
    @ApiResponse(responseCode = "400", description = "Solicitud inválida, código incorrecto (el retiro sigue "
            + "pendiente), o la referencia no es de un retiro")
    @ApiResponse(responseCode = "403", description = "Sin permiso, o la cuenta ya no está activa")
    @ApiResponse(responseCode = "404", description = "Retiro no encontrado")
    @ApiResponse(responseCode = "409", description = "Código vencido, retiro que ya no está pendiente, saldo "
            + "insuficiente, o un tope diario superado (solo clientes)")
    @ApiResponse(responseCode = "429", description = "Intentos agotados: el retiro queda cancelado")
    @PostMapping("/withdrawals/{reference}/confirm")
    public ResponseEntity<TransactionResponse> confirm(
            @CurrentActor Actor actor,
            @Parameter(description = "Referencia (comprobante) del retiro",
                    example = OpenApiExamples.WITHDRAWAL_REFERENCE) @PathVariable String reference,
            @Valid @RequestBody ConfirmWithdrawalRequest request) {
        return ResponseEntity.ok(confirmationService.confirm(actor, reference, request.getCode()));
    }
}
