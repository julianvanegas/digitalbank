package com.udea.digitalbank.accounts.controller;

import com.udea.digitalbank.accounts.dto.AccountBalanceResponse;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.dto.BlockAccountRequest;
import com.udea.digitalbank.accounts.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Todos los endpoints son de administradores: un empleado consulta la cuenta de cualquier cliente,
// en cualquier estado, sin verificación de propiedad
@RestController
@RequestMapping("/api/v1/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {

    private final AccountService accountService;

    public AdminAccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/by-number/{accountNumber}")
    public ResponseEntity<AccountResponse> accountByNumber(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getAnyAccountByNumber(accountNumber));
    }

    @GetMapping("/by-number/{accountNumber}/balance")
    public ResponseEntity<AccountBalanceResponse> balanceByNumber(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getAnyBalanceByNumber(accountNumber));
    }

    /**
     * Bloqueo temporal de una cuenta activa (HU15). El motivo es obligatorio y queda en el historial
     * junto con el administrador que lo ordenó, que se toma del token y no del cuerpo.
     */
    @PatchMapping("/by-number/{accountNumber}/block")
    public ResponseEntity<AccountResponse> blockAccount(Authentication auth,
                                                        @PathVariable String accountNumber,
                                                        @Valid @RequestBody BlockAccountRequest request) {
        // El principal es el id del usuario (sub del JWT)
        UUID actingUserId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(accountService.blockAccount(accountNumber, request.getReason(), actingUserId));
    }

    // Sin paginar: un cliente tiene como máximo tres cuentas. Tipo y número juntos, como el UNIQUE de customers
    @GetMapping("/by-customer")
    public ResponseEntity<List<AccountResponse>> accountsByCustomer(@RequestParam Short documentTypeId,
                                                                    @RequestParam String documentNumber) {
        return ResponseEntity.ok(accountService.getAccountsByDocument(documentTypeId, documentNumber));
    }

    @GetMapping("/by-customer/balances")
    public ResponseEntity<List<AccountBalanceResponse>> balancesByCustomer(@RequestParam Short documentTypeId,
                                                                           @RequestParam String documentNumber) {
        return ResponseEntity.ok(accountService.getBalancesByDocument(documentTypeId, documentNumber));
    }
}
