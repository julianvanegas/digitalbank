package com.udea.digitalbank.customer.controller;

import com.udea.digitalbank.customer.dto.ChangeStatusRequest;
import com.udea.digitalbank.customer.dto.CreateCustomerRequest;
import com.udea.digitalbank.customer.dto.CustomerResponse;
import com.udea.digitalbank.customer.dto.UpdateProfileRequest;
import com.udea.digitalbank.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    // Público (ver SecurityConfig): crea el cliente y su usuario en un solo request
    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(request));
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerResponse> myProfile(Authentication auth) {
        // El principal es el id del usuario (sub del JWT)
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(customerService.getProfile(userId));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerResponse> updateProfile(Authentication auth,
                                                           @Valid @RequestBody UpdateProfileRequest request) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(customerService.updateProfile(
                userId, request.getFirstNames(), request.getLastNames(), request.getPhone()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<CustomerResponse>> getAllCustomers(
            @PageableDefault(size = 20, sort = {"lastNames", "id"}) Pageable pageable) {
        return ResponseEntity.ok(customerService.getAllCustomers(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getCustomer(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> changeStatus(@PathVariable Long id,
                                             @Valid @RequestBody ChangeStatusRequest request) {
        customerService.changeStatus(id, request.getStatus());
        return ResponseEntity.noContent().build();
    }
}
