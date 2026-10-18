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
import com.udea.digitalbank.shared.config.OpenAPIConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import com.udea.digitalbank.shared.config.OpenApiExamples;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Los controllers solo traducen HTTP: qué rol entra a cada ruta lo decide AccountSecurity (roleRoutes) antes de llegar
 * aquí, y el servicio decide qué ve o hace el Actor.
 */
@Tag(name = OpenAPIConfig.TAG_ACCOUNTS)
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
    @Operation(summary = "Abrir una cuenta")
    @ApiResponse(responseCode = "201", description = "Cuenta abierta")
    @ApiResponse(responseCode = "400",
            description = "Solicitud inválida, o falta el titular al abrir como administrador")
    @ApiResponse(responseCode = "403",
            description = "Sin permiso, o intento de abrir una cuenta a nombre de otro cliente")
    @ApiResponse(responseCode = "404", description = "Cliente no encontrado")
    @ApiResponse(responseCode = "409", description = "Límite de cuentas abiertas alcanzado")
    @PostMapping
    public ResponseEntity<AccountResponse> open(@CurrentActor Actor actor,
                                                @Valid @RequestBody(required = false) CreateAccountRequest request) {
        UUID customerId = request == null ? null : request.getCustomerId();
        AccountTypeEnum accountType = request == null ? null : request.getAccountType();
        return ResponseEntity.status(HttpStatus.CREATED).body(openingService.open(actor, customerId, accountType));
    }

    /** Sin paginar: un cliente tiene como máximo tres cuentas abiertas. */
    @Operation(summary = "Listar mis cuentas")
    @ApiResponse(responseCode = "200", description = "Cuentas del cliente autenticado")
    @ApiResponse(responseCode = "404", description = "No existe un perfil de cliente para el usuario")
    @GetMapping
    public ResponseEntity<List<AccountResponse>> myAccounts(@CurrentActor Actor actor) {
        return ResponseEntity.ok(queryService.getMyAccounts(actor.userId()));
    }

    /**
     * Detalle de una cuenta por su número, que es el identificador que el cliente conoce. Un cliente solo
     * la obtiene si es suya (una ajena responde igual que una inexistente); un administrador, de cualquiera.
     */
    @Operation(summary = "Consultar una cuenta")
    @ApiResponse(responseCode = "200", description = "Cuenta encontrada")
    @ApiResponse(responseCode = "404", description = "La cuenta no existe o no es del usuario")
    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> account(
            @CurrentActor Actor actor,
            @Parameter(description = "Número de la cuenta (12 dígitos)",
                    example = OpenApiExamples.ACCOUNT_NUMBER,
                    schema = @Schema(pattern = "\\d{12}")) @PathVariable String accountNumber) {
        return ResponseEntity.ok(queryService.getAccount(actor, accountNumber));
    }

    /**
     * Solo administradores: cuentas de un cliente a partir de su documento, en cualquier estado.
     * Sin paginar: un cliente tiene como máximo tres cuentas abiertas. Tipo y número juntos, como el UNIQUE de
     * customers.
     */
    @Operation(summary = "Listar las cuentas de un cliente")
    @ApiResponse(responseCode = "200", description = "Cuentas del cliente")
    @ApiResponse(responseCode = "404", description = "Cliente no encontrado")
    @GetMapping("/by-customer")
    public ResponseEntity<List<AccountResponse>> accountsByCustomer(
            @Parameter(description = "Id del tipo de documento (ver GET /customers/document-types)",
                    example = OpenApiExamples.DOCUMENT_TYPE_ID) @RequestParam Short documentTypeId,
            @Parameter(description = "Número de documento del cliente",
                    example = OpenApiExamples.DOCUMENT_NUMBER) @RequestParam String documentNumber) {
        return ResponseEntity.ok(queryService.getAccountsByDocument(documentTypeId, documentNumber));
    }

    /**
     * Solo administradores: saldos de todas las cuentas de un cliente a partir de su documento.
     * Tipo y número juntos, como el UNIQUE de
     * customers.
     */
    @Operation(summary = "Consultar los saldos de un cliente")
    @ApiResponse(responseCode = "200", description = "Saldos de las cuentas del cliente")
    @ApiResponse(responseCode = "404", description = "Cliente no encontrado")
    @GetMapping("/by-customer/balances")
    public ResponseEntity<List<AccountBalanceResponse>> balancesByCustomer(
            @Parameter(description = "Id del tipo de documento (ver GET /customers/document-types)",
                    example = OpenApiExamples.DOCUMENT_TYPE_ID) @RequestParam Short documentTypeId,
            @Parameter(description = "Número de documento del cliente",
                    example = OpenApiExamples.DOCUMENT_NUMBER) @RequestParam String documentNumber) {
        return ResponseEntity.ok(queryService.getBalancesByDocument(documentTypeId, documentNumber));
    }

    /** Mismas reglas que el detalle: el cliente solo ve el saldo de sus cuentas, el administrador el de cualquiera. */
    @Operation(summary = "Consultar el saldo de una cuenta")
    @ApiResponse(responseCode = "200", description = "Saldo de la cuenta")
    @ApiResponse(responseCode = "404", description = "La cuenta no existe o no es del usuario")
    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<AccountBalanceResponse> balance(
            @CurrentActor Actor actor,
            @Parameter(description = "Número de la cuenta (12 dígitos)",
                    example = OpenApiExamples.ACCOUNT_NUMBER,
                    schema = @Schema(pattern = "\\d{12}")) @PathVariable String accountNumber) {
        return ResponseEntity.ok(queryService.getBalance(actor, accountNumber));
    }

    /**
     * Bloqueo temporal de una cuenta activa, solo para administradores. El motivo es obligatorio y queda
     * en el historial junto con el administrador que lo ordena, que se toma del token y no del cuerpo.
     */
    @Operation(summary = "Bloquear una cuenta")
    @ApiResponse(responseCode = "200", description = "Cuenta bloqueada")
    @ApiResponse(responseCode = "404", description = "La cuenta no existe")
    @ApiResponse(responseCode = "409", description = "La cuenta no se puede bloquear en su estado actual")
    @PatchMapping("/{accountNumber}/block")
    public ResponseEntity<AccountResponse> blockAccount(
            @CurrentActor Actor actor,
            @Parameter(description = "Número de la cuenta (12 dígitos)",
                    example = OpenApiExamples.ACCOUNT_NUMBER,
                    schema = @Schema(pattern = "\\d{12}")) @PathVariable String accountNumber,
            @Valid @RequestBody BlockAccountRequest request) {
        return ResponseEntity.ok(lifecycleService.blockAccount(accountNumber, request.getReason(), actor.userId()));
    }

    /**
     * Solo administradores: devuelve la cuenta de BLOCKED a ACTIVE. El motivo es obligatorio, como en el bloqueo,
     * y quien lo hizo se toma del token.
     */
    @Operation(summary = "Desbloquear una cuenta")
    @ApiResponse(responseCode = "200", description = "Cuenta desbloqueada")
    @ApiResponse(responseCode = "404", description = "La cuenta no existe")
    @ApiResponse(responseCode = "409", description = "La cuenta no se puede desbloquear en su estado actual")
    @PatchMapping("/{accountNumber}/unlock")
    public ResponseEntity<AccountResponse> unblockAccount(
            @CurrentActor Actor actor,
            @Parameter(description = "Número de la cuenta (12 dígitos)",
                    example = OpenApiExamples.ACCOUNT_NUMBER,
                    schema = @Schema(pattern = "\\d{12}")) @PathVariable String accountNumber,
            @Valid @RequestBody UnlockAccountRequest request) {
        return ResponseEntity.ok(lifecycleService.unblockAccount(accountNumber, request.getReason(), actor.userId()));
    }

    /**
     * Cierre de una cuenta por su número, sin cuerpo. Un cliente solo cierra las suyas (una ajena responde igual
     * que una inexistente); un administrador, cualquiera. Debe tener saldo cero y no estar bloqueada. Queda
     * registrado quién la cierra y se avisa al titular por correo.
     */
    @Operation(summary = "Cerrar una cuenta")
    @ApiResponse(responseCode = "200", description = "Cuenta cerrada")
    @ApiResponse(responseCode = "404", description = "La cuenta no existe o no es del usuario")
    @ApiResponse(responseCode = "409",
            description = "La cuenta no se puede cerrar: está bloqueada, ya cerrada o tiene saldo")
    @PatchMapping("/{accountNumber}/close")
    public ResponseEntity<AccountResponse> closeAccount(
            @CurrentActor Actor actor,
            @Parameter(description = "Número de la cuenta (12 dígitos)",
                    example = OpenApiExamples.ACCOUNT_NUMBER,
                    schema = @Schema(pattern = "\\d{12}")) @PathVariable String accountNumber) {
        return ResponseEntity.ok(lifecycleService.closeAccount(actor, accountNumber));
    }
}
