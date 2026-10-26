package com.udea.digitalbank.employee.service;

import com.udea.digitalbank.auth.api.RoleEnum;
import com.udea.digitalbank.auth.api.UserApi;
import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.employee.api.EmployeeView;
import com.udea.digitalbank.employee.domain.Employee;
import com.udea.digitalbank.employee.dto.CreateEmployeeRequest;
import com.udea.digitalbank.employee.dto.EmployeeResponse;
import com.udea.digitalbank.employee.mapper.EmployeeMapper;
import com.udea.digitalbank.employee.repository.EmployeeRepository;
import com.udea.digitalbank.shared.exception.auth.DuplicateUserException;
import com.udea.digitalbank.shared.exception.employee.EmployeeNotActiveException;
import com.udea.digitalbank.shared.exception.employee.EmployeeNotFoundException;
import com.udea.digitalbank.shared.exception.employee.OwnStatusChangeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) del módulo employee: los empleados son los administradores que operan las HU de
 * administración (HU08, HU15, HU16, HU27 y HU28). No hay una HU propia del módulo, por eso las pruebas son
 * transversales. El mapper es el real generado por MapStruct; el repositorio y auth se simulan.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Transversal - EmployeeService y EmployeeApiService")
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private UserApi userApi;

    private final EmployeeMapper mapper = Mappers.getMapper(EmployeeMapper.class);
    private EmployeeService employeeService;
    private EmployeeApiService employeeApiService;

    @BeforeEach
    void setUp() {
        EmployeeLookup lookup = new EmployeeLookup(employeeRepository, userApi);
        employeeService = new EmployeeService(employeeRepository, userApi, mapper, lookup);
        employeeApiService = new EmployeeApiService(employeeRepository, lookup, mapper);
    }

    private static Employee employee(UUID userId) {
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setUserId(userId);
        employee.setFirstNames("Carlos Andrés");
        employee.setLastNames("Ríos Mejía");
        return employee;
    }

    private static UserView user(UUID userId, String status) {
        return new UserView(userId, "carlos.rios@example.com", "ADMIN", status);
    }

    @Nested
    @DisplayName("Transversal - create")
    class Create {

        @Test
        @DisplayName("Transversal - Crea el usuario ADMIN en auth y el perfil del empleado, pendiente de verificación")
        void deberiaCrearElEmpleado() {
            // Arrange
            UUID userId = UUID.randomUUID();
            CreateEmployeeRequest request = new CreateEmployeeRequest();
            request.setFirstNames("Carlos Andrés");
            request.setLastNames("Ríos Mejía");
            request.setEmail("carlos.rios@example.com");
            when(userApi.createUser("carlos.rios@example.com", RoleEnum.ADMIN))
                    .thenReturn(user(userId, "PENDING_VERIFICATION"));
            when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            EmployeeResponse response = employeeService.create(request);

            // Assert
            ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
            verify(employeeRepository).save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(userId);
            assertThat(response.getFirstNames()).isEqualTo("Carlos Andrés");
            assertThat(response.getRole()).isEqualTo("ADMIN");
            assertThat(response.getStatus()).isEqualTo("PENDING_VERIFICATION");
            assertThat(response.getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("Transversal - Un correo ya registrado se rechaza y no se crea el perfil")
        void deberiaRechazarUnCorreoDuplicado() {
            // Arrange
            CreateEmployeeRequest request = new CreateEmployeeRequest();
            request.setEmail("carlos.rios@example.com");
            when(userApi.createUser("carlos.rios@example.com", RoleEnum.ADMIN))
                    .thenThrow(new DuplicateUserException("Ya existe un usuario registrado con ese email"));

            // Act + Assert
            assertThatThrownBy(() -> employeeService.create(request)).isInstanceOf(DuplicateUserException.class);
            verify(employeeRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Transversal - perfil y consultas")
    class Queries {

        @Test
        @DisplayName("Transversal - getProfile devuelve el perfil del empleado autenticado con los datos de auth")
        void deberiaDevolverElPerfil() {
            // Arrange
            UUID userId = UUID.randomUUID();
            when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee(userId)));
            when(userApi.getUser(userId)).thenReturn(user(userId, "ACTIVE"));

            // Act
            EmployeeResponse response = employeeService.getProfile(userId);

            // Assert
            assertThat(response.getEmail()).isEqualTo("carlos.rios@example.com");
            assertThat(response.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("Transversal - getProfile y getEmployee lanzan EmployeeNotFoundException si no existe")
        void deberiaRechazarUnEmpleadoInexistente() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID employeeId = UUID.randomUUID();
            when(employeeRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> employeeService.getProfile(userId)).isInstanceOf(EmployeeNotFoundException.class);
            assertThatThrownBy(() -> employeeService.getEmployee(employeeId))
                    .isInstanceOf(EmployeeNotFoundException.class);
        }

        @Test
        @DisplayName("Transversal - updateProfile cambia solo los nombres enviados y conserva el resto")
        void deberiaActualizarSoloLoEnviado() {
            // Arrange
            UUID userId = UUID.randomUUID();
            Employee employee = employee(userId);
            when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee));
            when(employeeRepository.save(employee)).thenReturn(employee);
            when(userApi.getUser(userId)).thenReturn(user(userId, "ACTIVE"));

            // Act
            EmployeeResponse response = employeeService.updateProfile(userId, "Carlos", null);

            // Assert
            assertThat(response.getFirstNames()).isEqualTo("Carlos");
            assertThat(response.getLastNames()).isEqualTo("Ríos Mejía");
        }

        @Test
        @DisplayName("Transversal - updateProfile con ambos nombres los reemplaza")
        void deberiaActualizarAmbosNombres() {
            // Arrange
            UUID userId = UUID.randomUUID();
            Employee employee = employee(userId);
            when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee));
            when(employeeRepository.save(employee)).thenReturn(employee);
            when(userApi.getUser(userId)).thenReturn(user(userId, "ACTIVE"));

            // Act
            EmployeeResponse response = employeeService.updateProfile(userId, "Ana", "Gómez");

            // Assert
            assertThat(response.getFirstNames()).isEqualTo("Ana");
            assertThat(response.getLastNames()).isEqualTo("Gómez");
        }

        @Test
        @DisplayName("Transversal - getAllEmployees resuelve los usuarios de la página en una sola consulta")
        void deberiaListarLosEmpleados() {
            // Arrange
            UUID firstUser = UUID.randomUUID();
            UUID secondUser = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 20);
            Page<Employee> page = new PageImpl<>(List.of(employee(firstUser), employee(secondUser)), pageable, 2);
            when(employeeRepository.findAll(pageable)).thenReturn(page);
            when(userApi.getUsers(List.of(firstUser, secondUser)))
                    .thenReturn(List.of(user(firstUser, "ACTIVE"), user(secondUser, "BLOCKED")));

            // Act
            Page<EmployeeResponse> result = employeeService.getAllEmployees(pageable);

            // Assert
            assertThat(result.getContent()).extracting(EmployeeResponse::getStatus)
                    .containsExactly("ACTIVE", "BLOCKED");
        }
    }

    @Nested
    @DisplayName("Transversal - changeStatus")
    class ChangeStatus {

        @Test
        @DisplayName("Transversal - Un administrador cambia el estado de otro empleado")
        void deberiaCambiarElEstadoDeOtroEmpleado() {
            // Arrange
            UUID userId = UUID.randomUUID();
            Employee employee = employee(userId);
            when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));

            // Act
            employeeService.changeStatus(employee.getId(), UserStatusEnum.BLOCKED, UUID.randomUUID());

            // Assert
            verify(userApi).changeStatus(userId, UserStatusEnum.BLOCKED);
        }

        @Test
        @DisplayName("Transversal - Un administrador no puede cambiar su propio estado")
        void noDeberiaCambiarElPropioEstado() {
            // Arrange
            UUID userId = UUID.randomUUID();
            Employee employee = employee(userId);
            when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));

            // Act + Assert
            assertThatThrownBy(() -> employeeService.changeStatus(employee.getId(), UserStatusEnum.INACTIVE, userId))
                    .isInstanceOf(OwnStatusChangeException.class);
            verify(userApi, never()).changeStatus(any(), any());
        }
    }

    @Nested
    @DisplayName("Transversal - EmployeeApiService")
    class Api {

        @Test
        @DisplayName("Transversal - getEmployee y getEmployeeByUserId combinan el perfil con el usuario de auth")
        void deberiaDevolverLaVista() {
            // Arrange
            UUID userId = UUID.randomUUID();
            Employee employee = employee(userId);
            when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
            when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee));
            when(userApi.getUser(userId)).thenReturn(user(userId, "ACTIVE"));

            // Act
            EmployeeView byId = employeeApiService.getEmployee(employee.getId());
            EmployeeView byUser = employeeApiService.getEmployeeByUserId(userId);

            // Assert
            assertThat(byId).isEqualTo(byUser);
            assertThat(byId.email()).isEqualTo("carlos.rios@example.com");
            assertThat(byId.userId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("Transversal - getEmployees resuelve los usuarios en una sola llamada")
        void deberiaListarLasVistas() {
            // Arrange
            UUID userId = UUID.randomUUID();
            Employee employee = employee(userId);
            when(employeeRepository.findAllById(List.of(employee.getId()))).thenReturn(List.of(employee));
            when(userApi.getUsers(List.of(userId))).thenReturn(List.of(user(userId, "ACTIVE")));

            // Act
            List<EmployeeView> views = employeeApiService.getEmployees(List.of(employee.getId()));

            // Assert
            assertThat(views).singleElement().extracting(EmployeeView::status).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("Transversal - requireActiveEmployee acepta un empleado ACTIVE y rechaza uno que no lo está")
        void deberiaExigirUnEmpleadoActivo() {
            // Arrange
            UUID activeUser = UUID.randomUUID();
            UUID blockedUser = UUID.randomUUID();
            Employee active = employee(activeUser);
            Employee blocked = employee(blockedUser);
            when(employeeRepository.findById(active.getId())).thenReturn(Optional.of(active));
            when(employeeRepository.findById(blocked.getId())).thenReturn(Optional.of(blocked));
            when(userApi.getUser(activeUser)).thenReturn(user(activeUser, "ACTIVE"));
            when(userApi.getUser(blockedUser)).thenReturn(user(blockedUser, "BLOCKED"));

            // Act + Assert
            assertThat(employeeApiService.requireActiveEmployee(active.getId()).status()).isEqualTo("ACTIVE");
            assertThatThrownBy(() -> employeeApiService.requireActiveEmployee(blocked.getId()))
                    .isInstanceOf(EmployeeNotActiveException.class);
        }
    }
}
