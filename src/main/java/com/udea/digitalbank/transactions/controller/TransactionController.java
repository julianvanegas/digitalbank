package com.udea.digitalbank.transactions.controller;

import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.shared.security.CurrentActor;
import com.udea.digitalbank.transactions.dto.ConfirmTransferRequest;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.dto.TransferRequest;
import com.udea.digitalbank.transactions.service.TransactionQueryService;
import com.udea.digitalbank.transactions.service.TransferConfirmationService;
import com.udea.digitalbank.transactions.service.TransferInitiationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.udea.digitalbank.shared.config.OpenAPIConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import com.udea.digitalbank.shared.config.OpenApiExamples;

/**
 * Endpoints de transacciones. El controller solo traduce HTTP y no contiene reglas de negocio:
 * <ul>
 *   <li>Qué rol puede usar cada ruta lo decide {@code TransactionSecurity} en la cadena de seguridad, antes de que
 *       la petición llegue aquí.</li>
 *   <li>Qué puede ver o hacer cada usuario lo decide el servicio a partir del {@link Actor}, que se resuelve desde el
 *       token y nunca desde datos de la petición.</li>
 * </ul>
 */
@Tag(name = OpenAPIConfig.TAG_TRANSACTIONS)
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransferInitiationService initiationService;
    private final TransferConfirmationService confirmationService;
    private final TransactionQueryService queryService;

    public TransactionController(TransferInitiationService initiationService,
                                 TransferConfirmationService confirmationService,
                                 TransactionQueryService queryService) {
        this.initiationService = initiationService;
        this.confirmationService = confirmationService;
        this.queryService = queryService;
    }

    /**
     * Inicia una transferencia ({@code POST /api/v1/transactions/transfers}): la deja {@code PENDING} y envía al
     * correo del ordenante el código de segundo factor que la autoriza.
     *
     * <p>Lo puede iniciar un cliente, desde sus propias cuentas y con los topes aplicados, o un administrador en
     * nombre de un cliente, desde la cuenta de cualquiera y sin topes. El código siempre llega al correo del titular de
     * la cuenta de origen.
     *
     * @param actor   usuario autenticado; es el ordenante
     * @param request cuentas de origen y destino, y monto
     * @return 201 con la referencia y el instante en que vence el código
     */
    @Operation(summary = "Iniciar una transferencia")
    @ApiResponse(responseCode = "201",
            description = "Transferencia creada en estado PENDING; el código se envía al correo")
    @ApiResponse(responseCode = "400",
            description = "Solicitud inválida, misma cuenta de origen y destino, o cuenta de destino inexistente "
                + "o inactiva")
    @ApiResponse(responseCode = "403", description = "Sin permiso, o la cuenta de origen no está activa")
    @ApiResponse(responseCode = "404", description = "La cuenta de origen no existe o no es del ordenante")
    @ApiResponse(responseCode = "409",
            description = "Fondos insuficientes, o el monto supera un tope diario (solo clientes)")
    @PostMapping("/transfers")
    public ResponseEntity<TransactionResponse> transfer(@CurrentActor Actor actor,
                                                        @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(initiationService.initiate(actor, request));
    }

    /**
     * Confirma una transferencia con su código ({@code POST /api/v1/transactions/{reference}/confirm}): con el código
     * correcto mueve el dinero y devuelve el comprobante.
     *
     * <p>Respuestas de error: 400 si el código es incorrecto (la transferencia sigue pendiente y se informan los
     * intentos restantes); 409 si el código ha vencido y 429 si se han agotado los intentos (en ambos casos la
     * transferencia queda cancelada y se debe iniciar una nueva).
     *
     * <p>Si la inició un administrador, la confirma él con el código que recibió el titular de la cuenta de origen.
     *
     * @param actor     usuario autenticado; debe ser el ordenante
     * @param reference comprobante de la transferencia
     * @param request   código de 6 dígitos recibido por correo
     * @return 200 con el comprobante de la transferencia completada
     */
    @Operation(summary = "Confirmar una transferencia")
    @ApiResponse(responseCode = "200", description = "Transferencia completada")
    @ApiResponse(responseCode = "400",
            description = "Solicitud inválida, o código incorrecto (la transferencia sigue pendiente)")
    @ApiResponse(responseCode = "403", description = "Sin permiso, o una de las cuentas ya no está activa")
    @ApiResponse(responseCode = "404", description = "Transferencia no encontrada")
    @ApiResponse(responseCode = "409",
            description = "Código vencido, transferencia que ya no está pendiente, fondos insuficientes, o un tope "
                + "diario superado (solo clientes)")
    @ApiResponse(responseCode = "429", description = "Intentos agotados: la transferencia queda cancelada")
    @PostMapping("/{reference}/confirm")
    public ResponseEntity<TransactionResponse> confirm(
            @CurrentActor Actor actor,
            @Parameter(description = "Referencia (comprobante) de la transferencia",
                    example = OpenApiExamples.REFERENCE) @PathVariable String reference,
            @Valid @RequestBody ConfirmTransferRequest request) {
        return ResponseEntity.ok(confirmationService.confirm(actor, reference, request.getCode()));
    }

    /**
     * Consulta el comprobante de una transacción ({@code GET /api/v1/transactions/{reference}}). Lo obtiene su
     * ordenante o un administrador; la de otro usuario responde igual que una inexistente (404).
     *
     * @param actor     usuario autenticado
     * @param reference comprobante de la transacción
     * @return 200 con el comprobante
     */
    @Operation(summary = "Consultar el comprobante de una transacción")
    @ApiResponse(responseCode = "200", description = "Comprobante de la transacción")
    @ApiResponse(responseCode = "404", description = "Transacción no encontrada")
    @GetMapping("/{reference}")
    public ResponseEntity<TransactionResponse> transaction(
            @CurrentActor Actor actor,
            @Parameter(description = "Referencia (comprobante) de la transferencia",
                    example = OpenApiExamples.REFERENCE) @PathVariable String reference) {
        return ResponseEntity.ok(queryService.getTransaction(actor, reference));
    }
}
