package com.udea.digitalbank.accounts.controller;

import com.udea.digitalbank.accounts.api.AccountTypeEnum;
import com.udea.digitalbank.accounts.dto.AccountBalanceResponse;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.dto.BlockAccountRequest;
import com.udea.digitalbank.accounts.dto.CreateAccountRequest;
import com.udea.digitalbank.accounts.dto.UnlockAccountRequest;
import com.udea.digitalbank.accounts.service.AccountLifecycleService;
import com.udea.digitalbank.accounts.service.AccountOpeningService;
import com.udea.digitalbank.accounts.service.AccountQueryService;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.shared.security.CurrentActor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Los controllers solo traducen HTTP: qué rol entra a cada ruta lo decide AccountSecurity (roleRoutes) antes de llegar
 * aquí, y el servicio decide qué ve o hace el Actor.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountOpeningService openingService;
    private final AccountQueryService queryService;
    private final AccountLifecycleService lifecycleService;

    public AccountController(AccountOpeningService openingService,
                             AccountQueryService queryService,
                             AccountLifecycleService lifecycleService) {
        this.openingService = openingService;
        this.queryService = queryService;
        this.lifecycleService = lifecycleService;
    }

    /**
     * Un cliente abre su propia cuenta y puede hacerlo sin cuerpo; un administrador la abre en
     * nombre de un cliente y entonces el customerId es obligatorio. El tipo es opcional: sin él es SAVINGS.
     */
    @PostMapping
    public ResponseEntity<AccountResponse> open(@CurrentActor Actor actor,
                                                @Valid @RequestBody(required = false) CreateAccountRequest request) {
        UUID customerId = request == null ? null : request.getCustomerId();
        AccountTypeEnum accountType = request == null ? null : request.getAccountType();
        return ResponseEntity.status(HttpStatus.CREATED).body(openingService.open(actor, customerId, accountType));
    }

    /** Sin paginar: un cliente tiene como máximo tres cuentas abiertas. */
    @GetMapping
    public ResponseEntity<List<AccountResponse>> myAccounts(@CurrentActor Actor actor) {
        return ResponseEntity.ok(queryService.getMyAccounts(actor.userId()));
    }

    /**
     * Detalle de una cuenta por su número, que es el identificador que el cliente conoce. Un cliente solo
     * la obtiene si es suya (una ajena responde igual que una inexistente); un administrador, de cualquiera.
     */
    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> account(@CurrentActor Actor actor, @PathVariable String accountNumber) {
        return ResponseEntity.ok(queryService.getAccount(actor, accountNumber));
    }

    /**
     * Solo administradores: cuentas de un cliente a partir de su documento, en cualquier estado.
     * Sin paginar: un cliente tiene como máximo tres cuentas abiertas. Tipo y número juntos, como el UNIQUE de
     * customers.
     */
    @GetMapping("/by-customer")
    public ResponseEntity<List<AccountResponse>> accountsByCustomer(@RequestParam Short documentTypeId,
                                                                    @RequestParam String documentNumber) {
        return ResponseEntity.ok(queryService.getAccountsByDocument(documentTypeId, documentNumber));
    }

    /**
     * Solo administradores: saldos de todas las cuentas de un cliente a partir de su documento.
     * Tipo y número juntos, como el UNIQUE de
     * customers.
     */
    @GetMapping("/by-customer/balances")
    public ResponseEntity<List<AccountBalanceResponse>> balancesByCustomer(@RequestParam Short documentTypeId,
                                                                           @RequestParam String documentNumber) {
        return ResponseEntity.ok(queryService.getBalancesByDocument(documentTypeId, documentNumber));
    }

    /** Mismas reglas que el detalle: el cliente solo ve el saldo de sus cuentas, el administrador el de cualquiera. */
    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<AccountBalanceResponse> balance(@CurrentActor Actor actor, @PathVariable String accountNumber) {
        return ResponseEntity.ok(queryService.getBalance(actor, accountNumber));
    }

    /**
     * Bloqueo temporal de una cuenta activa, solo para administradores. El motivo es obligatorio y queda
     * en el historial junto con el administrador que lo ordena, que se toma del token y no del cuerpo.
     */
    @PatchMapping("/{accountNumber}/block")
    public ResponseEntity<AccountResponse> blockAccount(@CurrentActor Actor actor,
                                                        @PathVariable String accountNumber,
                                                        @Valid @RequestBody BlockAccountRequest request) {
        return ResponseEntity.ok(lifecycleService.blockAccount(accountNumber, request.getReason(), actor.userId()));
    }

    /**
     * Solo administradores: devuelve la cuenta de BLOCKED a ACTIVE. El motivo es obligatorio, como en el bloqueo,
     * y quien lo hizo se toma del token.
     */
    @PatchMapping("/{accountNumber}/unlock")
    public ResponseEntity<AccountResponse> unblockAccount(@CurrentActor Actor actor,
                                                          @PathVariable String accountNumber,
                                                          @Valid @RequestBody UnlockAccountRequest request) {
        return ResponseEntity.ok(lifecycleService.unblockAccount(accountNumber, request.getReason(), actor.userId()));
    }

    /**
     * Cierre de una cuenta por su número, sin cuerpo. Un cliente solo cierra las suyas (una ajena responde igual
     * que una inexistente); un administrador, cualquiera. Debe tener saldo cero y no estar bloqueada. Queda
     * registrado quién la cierra y se avisa al titular por correo.
     */
    @PatchMapping("/{accountNumber}/close")
    public ResponseEntity<AccountResponse> closeAccount(@CurrentActor Actor actor, @PathVariable String accountNumber) {
        return ResponseEntity.ok(lifecycleService.closeAccount(actor, accountNumber));
    }
}
