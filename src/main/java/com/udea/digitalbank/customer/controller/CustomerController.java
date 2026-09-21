package com.udea.digitalbank.customer.controller;

import com.udea.digitalbank.auth.domain.StatusCode;
import com.udea.digitalbank.customer.dto.CreateCustomerRequest;
import com.udea.digitalbank.customer.dto.CustomerResponse;
import com.udea.digitalbank.customer.dto.UpdateProfileRequest;
import com.udea.digitalbank.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    // Público (ver SecurityConfig): crea el cliente y su cuenta en un solo request
    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(request));
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerResponse> myProfile(Authentication auth) {
        // El principal es el id de la cuenta (sub del JWT)
        Long accountId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(customerService.getProfile(accountId));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerResponse> updateProfile(Authentication auth,
                                                           @Valid @RequestBody UpdateProfileRequest request) {
        Long accountId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(customerService.updateProfile(accountId, request.getName(), request.getPhone()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {
        return ResponseEntity.ok(customerService.getAllCustomers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getCustomer(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> changeStatus(@PathVariable Long id, @RequestParam String status) {
        customerService.changeStatus(id, StatusCode.valueOf(status.trim().toUpperCase()));
        return ResponseEntity.noContent().build();
    }
}
