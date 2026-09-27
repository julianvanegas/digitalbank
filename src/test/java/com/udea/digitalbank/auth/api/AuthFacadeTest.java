package com.udea.digitalbank.auth.api;

import com.udea.digitalbank.auth.domain.Role;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.domain.UserStatus;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.auth.service.Catalogs;
import com.udea.digitalbank.auth.service.UserCreatedEvent;
import com.udea.digitalbank.auth.service.UserStatusService;
import com.udea.digitalbank.shared.exception.auth.DuplicateUserException;
import com.udea.digitalbank.shared.exception.auth.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) para AuthFacade.
 * Aquí quedó, tras la reestructuración, la parte de HU01 que antes vivía en CustomerService:
 * unicidad de email (CA06/CA07) y el hash de la contraseña. El registro completo de HU01 se
 * cubre combinando esta clase con CustomerServiceTest (edad, tipo de documento, documento duplicado).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthFacade - soporte de HU01 (unicidad de email, creación de usuario)")
class AuthFacadeTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserStatusService userStatusService;

    @Mock
    private Catalogs catalogs;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AuthFacade authFacade;

    @BeforeEach
    void setUp() {
        authFacade = new AuthFacade(userRepository, userStatusService, catalogs, passwordEncoder, eventPublisher);
    }

    private Role buildRole(short id, String code) {
        Role role = new Role();
        ReflectionTestUtils.setField(role, "id", id);
        ReflectionTestUtils.setField(role, "code", code);
        return role;
    }

    private UserStatus buildStatus(short id, String code) {
        UserStatus status = new UserStatus();
        ReflectionTestUtils.setField(status, "id", id);
        ReflectionTestUtils.setField(status, "code", code);
        return status;
    }

    @Nested
    @DisplayName("createUser - HU01 (CA06, CA07, y parte de CA01)")
    class CreateUserTests {

        @Test
        @DisplayName("CA07 - Rechaza la creación si el email ya está registrado")
        void hu01_ca07_deberiaRechazarCreacionSiEmailYaExiste() {
            // Arrange
            when(userRepository.existsByEmail("ana@example.com")).thenReturn(true);

            // Act & Assert
            assertThrows(DuplicateUserException.class,
                    () -> authFacade.createUser("ana@example.com", "Passw0rd!", RoleEnum.CUSTOMER));

            // No debe llegar a codificar contraseña, guardar usuario ni publicar el evento
            verify(userRepository, never()).save(any(User.class));
            verifyNoInteractions(passwordEncoder, eventPublisher, catalogs);
        }

        @Test
        @DisplayName("CA06 - Acepta la creación si el email no está registrado")
        void hu01_ca06_deberiaAceptarCreacionSiEmailNoExiste() {
            // Arrange
            when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
            when(catalogs.role(RoleEnum.CUSTOMER)).thenReturn(buildRole((short) 1, "CUSTOMER"));
            when(catalogs.status(UserStatusEnum.PENDING_VERIFICATION))
                    .thenReturn(buildStatus((short) 2, "PENDING_VERIFICATION"));
            when(passwordEncoder.encode("Passw0rd!")).thenReturn("hashed-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });

            // Act
            UserView result = authFacade.createUser("ana@example.com", "Passw0rd!", RoleEnum.CUSTOMER);

            // Assert
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.email()).isEqualTo("ana@example.com");
            assertThat(result.role()).isEqualTo("CUSTOMER");
            assertThat(result.status()).isEqualTo("PENDING_VERIFICATION");
        }

        @Test
        @DisplayName("La contraseña se guarda codificada, nunca en texto plano")
        void deberiaGuardarLaContrasenaCodificada() {
            // Arrange
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(catalogs.role(any())).thenReturn(buildRole((short) 1, "CUSTOMER"));
            when(catalogs.status(any())).thenReturn(buildStatus((short) 2, "PENDING_VERIFICATION"));
            when(passwordEncoder.encode("Passw0rd!")).thenReturn("hashed-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            authFacade.createUser("ana@example.com", "Passw0rd!", RoleEnum.CUSTOMER);

            // Assert
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-password");
        }

        @Test
        @DisplayName("El usuario recién creado queda en estado PENDING_VERIFICATION, no ACTIVE")
        void deberiaCrearUsuarioEnEstadoPendingVerification() {
            // Arrange
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(catalogs.role(any())).thenReturn(buildRole((short) 1, "CUSTOMER"));
            when(catalogs.status(UserStatusEnum.PENDING_VERIFICATION))
                    .thenReturn(buildStatus((short) 2, "PENDING_VERIFICATION"));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            authFacade.createUser("ana@example.com", "Passw0rd!", RoleEnum.CUSTOMER);

            // Assert: se pidió explícitamente el estado PENDING_VERIFICATION al catálogo
            verify(catalogs).status(UserStatusEnum.PENDING_VERIFICATION);
        }

        @Test
        @DisplayName("Publica UserCreatedEvent al crear el usuario (dispara el envío del correo de confirmación)")
        void deberiaPublicarEventoDeUsuarioCreado() {
            // Arrange
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(catalogs.role(any())).thenReturn(buildRole((short) 1, "CUSTOMER"));
            when(catalogs.status(any())).thenReturn(buildStatus((short) 2, "PENDING_VERIFICATION"));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(5L);
                return user;
            });

            // Act
            authFacade.createUser("ana@example.com", "Passw0rd!", RoleEnum.CUSTOMER);

            // Assert
            ArgumentCaptor<UserCreatedEvent> eventCaptor = ArgumentCaptor.forClass(UserCreatedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().userId()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("getUser / getUsers")
    class GetUserTests {

        @Test
        @DisplayName("Retorna la vista del usuario cuando existe")
        void deberiaRetornarUsuarioCuandoExiste() {
            // Arrange
            User user = new User();
            user.setId(1L);
            user.setEmail("ana@example.com");
            user.setRole(buildRole((short) 1, "CUSTOMER"));
            user.setStatus(buildStatus((short) 3, "ACTIVE"));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            // Act
            UserView result = authFacade.getUser(1L);

            // Assert
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.email()).isEqualTo("ana@example.com");
            assertThat(result.role()).isEqualTo("CUSTOMER");
            assertThat(result.status()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("Lanza excepción cuando el usuario no existe")
        void deberiaLanzarExcepcionCuandoUsuarioNoExiste() {
            // Arrange
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(UserNotFoundException.class, () -> authFacade.getUser(99L));
        }

        @Test
        @DisplayName("getUsers retorna la vista de cada usuario encontrado")
        void deberiaRetornarVistasDeVariosUsuarios() {
            // Arrange
            User user1 = new User();
            user1.setId(1L);
            user1.setEmail("uno@example.com");
            user1.setRole(buildRole((short) 1, "CUSTOMER"));
            user1.setStatus(buildStatus((short) 3, "ACTIVE"));

            User user2 = new User();
            user2.setId(2L);
            user2.setEmail("dos@example.com");
            user2.setRole(buildRole((short) 1, "CUSTOMER"));
            user2.setStatus(buildStatus((short) 3, "ACTIVE"));

            when(userRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(user1, user2));

            // Act
            List<UserView> result = authFacade.getUsers(List.of(1L, 2L));

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result).extracting(UserView::email)
                    .containsExactlyInAnyOrder("uno@example.com", "dos@example.com");
        }
    }

    @Nested
    @DisplayName("changeStatus")
    class ChangeStatusTests {

        @Test
        @DisplayName("Delega el cambio de estado en UserStatusService")
        void deberiaDelegarCambioDeEstado() {
            // Act
            authFacade.changeStatus(1L, UserStatusEnum.BLOCKED);

            // Assert
            verify(userStatusService).changeStatus(1L, UserStatusEnum.BLOCKED);
        }
    }
}