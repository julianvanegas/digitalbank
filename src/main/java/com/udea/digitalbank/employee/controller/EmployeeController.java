package com.udea.digitalbank.employee.controller;

import com.udea.digitalbank.employee.dto.ChangeEmployeeStatusRequest;
import com.udea.digitalbank.employee.dto.CreateEmployeeRequest;
import com.udea.digitalbank.employee.dto.EmployeeResponse;
import com.udea.digitalbank.employee.dto.UpdateEmployeeProfileRequest;
import com.udea.digitalbank.employee.service.EmployeeService;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.shared.security.CurrentActor;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// Todos los endpoints son de administradores (lo exige EmployeeSecurity): los empleados son los únicos usuarios con rol ADMIN
@RestController
@RequestMapping("/api/v1/employees")
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
    public ResponseEntity<EmployeeResponse> myProfile(@CurrentActor Actor actor) {
        return ResponseEntity.ok(employeeService.getProfile(actor.userId()));
    }

    @PutMapping("/profile")
    public ResponseEntity<EmployeeResponse> updateProfile(@CurrentActor Actor actor,
                                                           @Valid @RequestBody UpdateEmployeeProfileRequest request) {
        return ResponseEntity.ok(employeeService.updateProfile(
                actor.userId(), request.getFirstNames(), request.getLastNames()));
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
    public ResponseEntity<Void> changeStatus(@CurrentActor Actor actor, @PathVariable UUID id,
                                             @Valid @RequestBody ChangeEmployeeStatusRequest request) {
        employeeService.changeStatus(id, request.getStatus(), actor.userId());
        return ResponseEntity.noContent().build();
    }
}
