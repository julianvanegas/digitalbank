package com.udea.digitalbank.employee.controller;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.employee.dto.ChangeEmployeeStatusRequest;
import com.udea.digitalbank.employee.dto.CreateEmployeeRequest;
import com.udea.digitalbank.employee.dto.EmployeeResponse;
import com.udea.digitalbank.employee.dto.UpdateEmployeeProfileRequest;
import com.udea.digitalbank.employee.service.EmployeeService;
import com.udea.digitalbank.shared.security.Actor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link EmployeeController}: se instancia directamente, igual que los demás
 * controllers. Solo traduce HTTP y pasa el actor y los datos al servicio.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Transversal - EmployeeController")
class EmployeeControllerTest {

    @Mock
    private EmployeeService employeeService;

    private EmployeeController controller;
    private final UUID userId = UUID.randomUUID();
    private final Actor admin = new Actor(userId, true);

    @BeforeEach
    void setUp() {
        controller = new EmployeeController(employeeService);
    }

    @Test
    @DisplayName("Transversal - create responde 201 con el empleado creado")
    void deberiaCrearElEmpleado() {
        // Arrange
        CreateEmployeeRequest request = new CreateEmployeeRequest();
        EmployeeResponse created = new EmployeeResponse();
        when(employeeService.create(request)).thenReturn(created);

        // Act
        ResponseEntity<EmployeeResponse> response = controller.create(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(created);
    }

    @Test
    @DisplayName("Transversal - myProfile y updateProfile usan el usuario del actor y responden 200")
    void deberiaUsarElActorEnElPerfil() {
        // Arrange
        EmployeeResponse profile = new EmployeeResponse();
        UpdateEmployeeProfileRequest request = new UpdateEmployeeProfileRequest();
        request.setFirstNames("Carlos");
        when(employeeService.getProfile(userId)).thenReturn(profile);
        when(employeeService.updateProfile(userId, "Carlos", null)).thenReturn(profile);

        // Act
        ResponseEntity<EmployeeResponse> read = controller.myProfile(admin);
        ResponseEntity<EmployeeResponse> updated = controller.updateProfile(admin, request);

        // Assert
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).isSameAs(profile);
    }

    @Test
    @DisplayName("Transversal - getAllEmployees y getEmployee delegan y responden 200")
    void deberiaConsultarEmpleados() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<EmployeeResponse> page = new PageImpl<>(List.of(new EmployeeResponse()), pageable, 1);
        UUID employeeId = UUID.randomUUID();
        EmployeeResponse one = new EmployeeResponse();
        when(employeeService.getAllEmployees(pageable)).thenReturn(page);
        when(employeeService.getEmployee(employeeId)).thenReturn(one);

        // Act
        ResponseEntity<Page<EmployeeResponse>> all = controller.getAllEmployees(pageable);
        ResponseEntity<EmployeeResponse> single = controller.getEmployee(employeeId);

        // Assert
        assertThat(all.getBody()).isSameAs(page);
        assertThat(single.getBody()).isSameAs(one);
    }

    @Test
    @DisplayName("Transversal - changeStatus pasa el empleado, el estado y el administrador que actúa; responde 204")
    void deberiaCambiarElEstado() {
        // Arrange
        UUID employeeId = UUID.randomUUID();
        ChangeEmployeeStatusRequest request = new ChangeEmployeeStatusRequest();
        request.setStatus(UserStatusEnum.INACTIVE);

        // Act
        ResponseEntity<Void> response = controller.changeStatus(admin, employeeId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(employeeService).changeStatus(employeeId, UserStatusEnum.INACTIVE, userId);
    }
}
