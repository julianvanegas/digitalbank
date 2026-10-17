package com.udea.digitalbank.customer.controller;

import com.udea.digitalbank.customer.dto.ChangeCustomerStatusRequest;
import com.udea.digitalbank.customer.dto.CreateCustomerRequest;
import com.udea.digitalbank.customer.dto.CustomerResponse;
import com.udea.digitalbank.customer.dto.DocumentTypeResponse;
import com.udea.digitalbank.customer.dto.UpdateCustomerProfileRequest;
import com.udea.digitalbank.customer.service.CustomerService;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.shared.security.CurrentActor;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /** Público (ver SecurityConfig): crea el cliente y su usuario en un solo request. */
    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(request));
    }

    /** Público (ver CustomerSecurity): el formulario de registro necesita los ids antes de tener token. */
    @GetMapping("/document-types")
    public ResponseEntity<List<DocumentTypeResponse>> documentTypes() {
        return ResponseEntity.ok(customerService.getDocumentTypes());
    }

    @GetMapping("/profile")
    public ResponseEntity<CustomerResponse> myProfile(@CurrentActor Actor actor) {
        return ResponseEntity.ok(customerService.getProfile(actor.userId()));
    }

    @PutMapping("/profile")
    public ResponseEntity<CustomerResponse> updateProfile(@CurrentActor Actor actor,
                                                           @Valid @RequestBody UpdateCustomerProfileRequest request) {
        return ResponseEntity.ok(customerService.updateProfile(
                actor.userId(), request.getFirstNames(), request.getLastNames(), request.getPhone()));
    }

    @GetMapping
    public ResponseEntity<Page<CustomerResponse>> getAllCustomers(
            @PageableDefault(size = 20, sort = {"lastNames", "id"}) Pageable pageable) {
        return ResponseEntity.ok(customerService.getAllCustomers(pageable));
    }

    /** Tipo y número juntos, como el UNIQUE de customers (igual que /accounts/by-customer). */
    @GetMapping("/by-document")
    public ResponseEntity<CustomerResponse> getCustomer(@RequestParam Short documentTypeId,
                                                        @RequestParam String documentNumber) {
        return ResponseEntity.ok(customerService.getCustomer(documentTypeId, documentNumber));
    }

    @PatchMapping("/status")
    public ResponseEntity<Void> changeStatus(@Valid @RequestBody ChangeCustomerStatusRequest request) {
        customerService.changeStatus(request.getDocumentTypeId(), request.getDocumentNumber(), request.getStatus());
        return ResponseEntity.noContent().build();
    }
}
