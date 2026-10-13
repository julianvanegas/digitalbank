package com.udea.digitalbank.accounts.controller;

import com.udea.digitalbank.accounts.dto.AccountBalanceResponse;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
