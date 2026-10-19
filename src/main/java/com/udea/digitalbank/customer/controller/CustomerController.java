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
import com.udea.digitalbank.shared.config.OpenAPIConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import com.udea.digitalbank.shared.config.OpenApiExamples;
import org.springdoc.core.annotations.ParameterObject;

@Tag(name = OpenAPIConfig.TAG_CUSTOMERS)
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /** Público (ver SecurityConfig): crea el cliente y su usuario en un solo request. */
    @Operation(operationId = "registerCustomer", summary = "Registrar un cliente")
    @ApiResponse(responseCode = "201", description = "Cliente registrado; queda pendiente de confirmar su correo")
    @ApiResponse(responseCode = "400",
            description = "Solicitud inválida, menor de edad o tipo de documento inexistente")
    @ApiResponse(responseCode = "409", description = "Ya existe un registro con esos datos")
    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(request));
    }

    /** Público (ver CustomerSecurity): el formulario de registro necesita los ids antes de tener token. */
    @Operation(summary = "Listar los tipos de documento")
    @ApiResponse(responseCode = "200", description = "Tipos de documento disponibles")
    @GetMapping("/document-types")
    public ResponseEntity<List<DocumentTypeResponse>> documentTypes() {
        return ResponseEntity.ok(customerService.getDocumentTypes());
    }

    @Operation(operationId = "getMyCustomerProfile", summary = "Consultar mi perfil de cliente")
    @ApiResponse(responseCode = "200", description = "Perfil del cliente autenticado")
    @ApiResponse(responseCode = "404", description = "No existe un perfil de cliente para el usuario")
    @GetMapping("/profile")
    public ResponseEntity<CustomerResponse> myProfile(@CurrentActor Actor actor) {
        return ResponseEntity.ok(customerService.getProfile(actor.userId()));
    }

    @Operation(operationId = "updateMyCustomerProfile", summary = "Actualizar mi perfil de cliente")
    @ApiResponse(responseCode = "200", description = "Perfil actualizado")
    @ApiResponse(responseCode = "404", description = "No existe un perfil de cliente para el usuario")
    @PutMapping("/profile")
    public ResponseEntity<CustomerResponse> updateProfile(@CurrentActor Actor actor,
                                                           @Valid @RequestBody UpdateCustomerProfileRequest request) {
        return ResponseEntity.ok(customerService.updateProfile(
                actor.userId(), request.getFirstNames(), request.getLastNames(), request.getPhone()));
    }

    @Operation(summary = "Listar clientes")
    @ApiResponse(responseCode = "200", description = "Página de clientes")
    @GetMapping
    public ResponseEntity<Page<CustomerResponse>> getAllCustomers(
            @ParameterObject @PageableDefault(size = 20, sort = {"lastNames", "id"}) Pageable pageable) {
        return ResponseEntity.ok(customerService.getAllCustomers(pageable));
    }

    /** Tipo y número juntos, como el UNIQUE de customers (igual que /accounts/by-customer). */
    @Operation(summary = "Buscar un cliente por documento")
    @ApiResponse(responseCode = "200", description = "Cliente encontrado")
    @ApiResponse(responseCode = "404", description = "Cliente no encontrado")
    @GetMapping("/by-document")
    public ResponseEntity<CustomerResponse> getCustomer(
            @Parameter(description = "Id del tipo de documento (ver GET /customers/document-types)",
                    example = OpenApiExamples.DOCUMENT_TYPE_ID) @RequestParam Short documentTypeId,
            @Parameter(description = "Número de documento del cliente",
                    example = OpenApiExamples.DOCUMENT_NUMBER) @RequestParam String documentNumber) {
        return ResponseEntity.ok(customerService.getCustomer(documentTypeId, documentNumber));
    }

    @Operation(operationId = "changeCustomerStatus", summary = "Cambiar el estado de un cliente")
    @ApiResponse(responseCode = "204", description = "Estado actualizado")
    @ApiResponse(responseCode = "404", description = "Cliente no encontrado")
    @ApiResponse(responseCode = "409", description = "Transición de estado no permitida")
    @PatchMapping("/status")
    public ResponseEntity<Void> changeStatus(@Valid @RequestBody ChangeCustomerStatusRequest request) {
        customerService.changeStatus(request.getDocumentTypeId(), request.getDocumentNumber(), request.getStatus());
        return ResponseEntity.noContent().build();
    }
}
