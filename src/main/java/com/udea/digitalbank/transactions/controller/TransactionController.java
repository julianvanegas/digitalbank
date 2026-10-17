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

// Los controllers solo traducen HTTP: qué rol entra a cada ruta lo decide TransactionSecurity (roleRoutes) antes de
// llegar aquí, y el servicio decide qué ve o hace el Actor
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
     * Primer paso de una transferencia: la deja PENDING y envía al correo del cliente el código de segundo
     * factor que la autoriza. El ordenante sale del token. Responde con la referencia y el momento en que vence el código.
     */
    @PostMapping("/transfers")
    public ResponseEntity<TransactionResponse> transfer(@CurrentActor Actor actor,
                                                        @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.initiate(actor, request));
    }

    /**
     * Segundo paso: con el código correcto mueve el dinero y devuelve el comprobante. Un código incorrecto deja
     * la transferencia pendiente (400); al tercer fallo (429) o si el código venció (409) se cancela.
     */
    @PostMapping("/{reference}/confirm")
    public ResponseEntity<TransactionResponse> confirm(@CurrentActor Actor actor,
                                                       @PathVariable String reference,
                                                       @Valid @RequestBody ConfirmTransferRequest request) {
        return ResponseEntity.ok(transferService.confirm(actor, reference, request.getCode()));
    }

    // Comprobante por referencia: quien ordenó la transacción, o un administrador. Una ajena responde como inexistente
    @GetMapping("/{reference}")
    public ResponseEntity<TransactionResponse> transaction(@CurrentActor Actor actor, @PathVariable String reference) {
        return ResponseEntity.ok(queryService.getTransaction(actor, reference));
    }
}
