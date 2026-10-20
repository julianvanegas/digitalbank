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
import com.udea.digitalbank.shared.config.OpenAPIConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import com.udea.digitalbank.shared.config.OpenApiExamples;
import org.springdoc.core.annotations.ParameterObject;

/**
 * Todos los endpoints son de administradores (lo exige EmployeeSecurity): los empleados son los únicos usuarios con
 * rol ADMIN.
 */
@Tag(name = OpenAPIConfig.TAG_EMPLOYEES)
@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {
    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @Operation(operationId = "createEmployee", summary = "Crear un empleado")
    @ApiResponse(responseCode = "201", description = "Empleado creado; queda pendiente de confirmar su correo")
    @ApiResponse(responseCode = "409", description = "Ya existe un registro con esos datos")
    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(request));
    }

    @Operation(operationId = "getMyEmployeeProfile", summary = "Consultar mi perfil de empleado")
    @ApiResponse(responseCode = "200", description = "Perfil del empleado autenticado")
    @ApiResponse(responseCode = "404", description = "Empleado no encontrado")
    @GetMapping("/profile")
    public ResponseEntity<EmployeeResponse> myProfile(@CurrentActor Actor actor) {
        return ResponseEntity.ok(employeeService.getProfile(actor.userId()));
    }

    @Operation(operationId = "updateMyEmployeeProfile", summary = "Actualizar mi perfil de empleado")
    @ApiResponse(responseCode = "200", description = "Perfil actualizado")
    @ApiResponse(responseCode = "404", description = "Empleado no encontrado")
    @PutMapping("/profile")
    public ResponseEntity<EmployeeResponse> updateProfile(@CurrentActor Actor actor,
                                                           @Valid @RequestBody UpdateEmployeeProfileRequest request) {
        return ResponseEntity.ok(employeeService.updateProfile(
                actor.userId(), request.getFirstNames(), request.getLastNames()));
    }

    @Operation(summary = "Listar empleados")
    @ApiResponse(responseCode = "200", description = "Página de empleados")
    @GetMapping
    public ResponseEntity<Page<EmployeeResponse>> getAllEmployees(
            @ParameterObject @PageableDefault(size = 20, sort = {"lastNames", "id"}) Pageable pageable) {
        return ResponseEntity.ok(employeeService.getAllEmployees(pageable));
    }

    @Operation(summary = "Consultar un empleado")
    @ApiResponse(responseCode = "200", description = "Empleado encontrado")
    @ApiResponse(responseCode = "404", description = "Empleado no encontrado")
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> getEmployee(
            @Parameter(description = "Identificador del empleado",
                    example = OpenApiExamples.EMPLOYEE_ID) @PathVariable UUID id) {
        return ResponseEntity.ok(employeeService.getEmployee(id));
    }

    @Operation(operationId = "changeEmployeeStatus", summary = "Cambiar el estado de un empleado")
    @ApiResponse(responseCode = "204", description = "Estado actualizado")
    @ApiResponse(responseCode = "403", description = "Sin permiso, o intento de cambiar el propio estado")
    @ApiResponse(responseCode = "404", description = "Empleado no encontrado")
    @ApiResponse(responseCode = "409", description = "Transición de estado no permitida")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(
            @CurrentActor Actor actor,
            @Parameter(description = "Identificador del empleado",
                    example = OpenApiExamples.EMPLOYEE_ID) @PathVariable UUID id,
            @Valid @RequestBody ChangeEmployeeStatusRequest request) {
        employeeService.changeStatus(id, request.getStatus(), actor.userId());
        return ResponseEntity.noContent().build();
    }
}
