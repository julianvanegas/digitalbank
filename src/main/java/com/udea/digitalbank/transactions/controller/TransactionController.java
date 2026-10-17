package com.udea.digitalbank.transactions.controller;

import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.shared.security.CurrentActor;
import com.udea.digitalbank.transactions.dto.ConfirmTransferRequest;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.dto.TransferRequest;
import com.udea.digitalbank.transactions.service.TransactionQueryService;
import com.udea.digitalbank.transactions.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints de transacciones. El controller solo traduce HTTP y no contiene reglas de negocio:
 * <ul>
 *   <li>Qué rol puede usar cada ruta lo decide {@code TransactionSecurity} en la cadena de seguridad, antes de que
 *       la petición llegue aquí.</li>
 *   <li>Qué puede ver o hacer cada usuario lo decide el servicio a partir del {@link Actor}, que se resuelve desde el
 *       token y nunca desde datos de la petición.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransferService transferService;
    private final TransactionQueryService queryService;

    public TransactionController(TransferService transferService, TransactionQueryService queryService) {
        this.transferService = transferService;
        this.queryService = queryService;
    }

    /**
     * Inicia una transferencia ({@code POST /api/v1/transactions/transfers}): la deja {@code PENDING} y envía al
     * correo del ordenante el código de segundo factor que la autoriza.
     *
     * @param actor   usuario autenticado; es el ordenante
     * @param request cuentas de origen y destino, y monto
     * @return 201 con la referencia y el instante en que vence el código
     */
    @PostMapping("/transfers")
    public ResponseEntity<TransactionResponse> transfer(@CurrentActor Actor actor,
                                                        @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.initiate(actor, request));
    }

    /**
     * Confirma una transferencia con su código ({@code POST /api/v1/transactions/{reference}/confirm}): con el código
     * correcto mueve el dinero y devuelve el comprobante.
     *
     * <p>Respuestas de error: 400 si el código es incorrecto (la transferencia sigue pendiente y se informan los
     * intentos restantes); 409 si el código ha vencido y 429 si se han agotado los intentos (en ambos casos la
     * transferencia queda cancelada y se debe iniciar una nueva).
     *
     * @param actor     usuario autenticado; debe ser el ordenante
     * @param reference comprobante de la transferencia
     * @param request   código de 6 dígitos recibido por correo
     * @return 200 con el comprobante de la transferencia completada
     */
    @PostMapping("/{reference}/confirm")
    public ResponseEntity<TransactionResponse> confirm(@CurrentActor Actor actor,
                                                       @PathVariable String reference,
                                                       @Valid @RequestBody ConfirmTransferRequest request) {
        return ResponseEntity.ok(transferService.confirm(actor, reference, request.getCode()));
    }

    /**
     * Consulta el comprobante de una transacción ({@code GET /api/v1/transactions/{reference}}). Lo obtiene su
     * ordenante o un administrador; la de otro usuario responde igual que una inexistente (404).
     *
     * @param actor     usuario autenticado
     * @param reference comprobante de la transacción
     * @return 200 con el comprobante
     */
    @GetMapping("/{reference}")
    public ResponseEntity<TransactionResponse> transaction(@CurrentActor Actor actor, @PathVariable String reference) {
        return ResponseEntity.ok(queryService.getTransaction(actor, reference));
    }
}
