package com.udea.digitalbank.accounts.controller;

import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.dto.CreateAccountRequest;
import com.udea.digitalbank.accounts.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Un cliente abre su propia cuenta y puede hacerlo sin cuerpo; un administrador la abre en
     * nombre de un cliente y entonces el customerId es obligatorio.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<AccountResponse> open(Authentication auth,
                                                @Valid @RequestBody(required = false) CreateAccountRequest request) {
        // El principal es el id del usuario (sub del JWT)
        UUID userId = (UUID) auth.getPrincipal();
        UUID customerId = request == null ? null : request.getCustomerId();

        AccountResponse response = isAdmin(auth)
                ? accountService.openForCustomer(requireCustomerId(customerId))
                : accountService.openForSelf(userId, customerId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Sin paginar: un cliente tiene como máximo tres cuentas
    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<AccountResponse>> myAccounts(Authentication auth) {
        return ResponseEntity.ok(accountService.getMyAccounts((UUID) auth.getPrincipal()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<AccountResponse> myAccount(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getMyAccount((UUID) auth.getPrincipal(), id));
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_AUTHORITY::equals);
    }

    private static UUID requireCustomerId(UUID customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException(
                    "Un administrador debe indicar el customerId del titular de la cuenta");
        }
        return customerId;
    }
}
