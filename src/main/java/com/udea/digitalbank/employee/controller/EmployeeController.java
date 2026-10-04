package com.udea.digitalbank.employee.controller;

import com.udea.digitalbank.employee.dto.ChangeStatusRequest;
import com.udea.digitalbank.employee.dto.CreateEmployeeRequest;
import com.udea.digitalbank.employee.dto.EmployeeResponse;
import com.udea.digitalbank.employee.dto.UpdateProfileRequest;
import com.udea.digitalbank.employee.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// Todos los endpoints son de administradores: los empleados son los únicos usuarios con rol ADMIN
@RestController
@RequestMapping("/api/v1/employees")
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeController {
    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(request));
    }

    @GetMapping("/profile")
    public ResponseEntity<EmployeeResponse> myProfile(Authentication auth) {
        // El principal es el id del usuario (sub del JWT)
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(employeeService.getProfile(userId));
    }

    @PutMapping("/profile")
    public ResponseEntity<EmployeeResponse> updateProfile(Authentication auth,
                                                           @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(employeeService.updateProfile(
                userId, request.getFirstNames(), request.getLastNames()));
    }

    @GetMapping
    public ResponseEntity<Page<EmployeeResponse>> getAllEmployees(
            @PageableDefault(size = 20, sort = {"lastNames", "id"}) Pageable pageable) {
        return ResponseEntity.ok(employeeService.getAllEmployees(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> getEmployee(@PathVariable UUID id) {
        return ResponseEntity.ok(employeeService.getEmployee(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(Authentication auth, @PathVariable UUID id,
                                             @Valid @RequestBody ChangeStatusRequest request) {
        employeeService.changeStatus(id, request.getStatus(), (UUID) auth.getPrincipal());
        return ResponseEntity.noContent().build();
    }
}
