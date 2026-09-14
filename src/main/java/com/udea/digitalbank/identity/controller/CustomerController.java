package com.udea.digitalbank.identity.controller;

import com.udea.digitalbank.identity.domain.CustomerStatus;
import com.udea.digitalbank.identity.dto.CustomerResponse;
import com.udea.digitalbank.identity.dto.UpdateProfileRequest;
import com.udea.digitalbank.identity.service.CustomerService;
import jakarta.validation.Valid;
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

    @GetMapping("/profile")
    public ResponseEntity<CustomerResponse> myProfile(Authentication auth) {
        Long customerId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(customerService.getProfile(customerId));
    }

    @PutMapping("/profile")
    public ResponseEntity<CustomerResponse> updateProfile(Authentication auth,
                                                           @Valid @RequestBody UpdateProfileRequest request) {
        Long customerId = (Long) auth.getPrincipal();
        CustomerResponse response = customerService.updateProfile(
                customerId, request.getName(), request.getPhone(), request.getEmail());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {
        return ResponseEntity.ok(customerService.getAllCustomers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getProfile(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> changeStatus(@PathVariable Long id,
                                             @RequestParam CustomerStatus status) {
        customerService.changeStatus(id, status);
        return ResponseEntity.noContent().build();
    }
}
