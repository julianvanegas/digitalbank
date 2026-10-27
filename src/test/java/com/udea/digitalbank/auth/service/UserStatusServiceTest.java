package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.domain.UserStatus;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.shared.exception.auth.InvalidStatusTransitionException;
import com.udea.digitalbank.shared.exception.auth.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link UserStatusService}.
 * Es la clase que de verdad implementa el bloqueo tras 3 intentos fallidos y los
 * cambios de estado que revocan la sesión. AuthServiceTest solo la simula con mocks, así que esta clase
 * verifica la regla de forma directa.
 */
@ExtendWith(MockitoExtension.class)
class UserStatusServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private Catalogs catalogs;

    @InjectMocks
    private UserStatusService userStatusService;

    private static User buildUser(UUID id, UserStatusEnum statusEnum, int failedAttempts) {
        User user = new User();
        user.setId(id);
        user.setStatus(buildStatus(statusEnum));
        user.setFailedAttempts((short) failedAttempts);
        return user;
    }

    // UserStatus es @Immutable y solo expone getters: se arma por reflexión únicamente para el fixture
    private static UserStatus buildStatus(UserStatusEnum statusEnum) {
        UserStatus status = new UserStatus();
        setField(status, "code", statusEnum.name());
        return status;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("HU02 / HU09 - registerFailedAttempt - bloqueo tras 3 intentos fallidos")
    class RegisterFailedAttempt {

        @Test
        @DisplayName("HU09 CA02 - Primer intento fallido: suma uno y no bloquea")
        void deberiaSumarUnIntentoSinBloquearEnElPrimerFallo() {
            // Arrange
            User user = buildUser(UUID.randomUUID(), UserStatusEnum.ACTIVE, 0);

            // Act
            userStatusService.registerFailedAttempt(user);

            // Assert
            assertThat(user.getFailedAttempts()).isEqualTo((short) 1);
            assertThat(user.getStatus().getCode()).isEqualTo("ACTIVE"); // sigue activo
            verify(userRepository, times(1)).save(user); // solo el save final, no pasa por applyStatus
            verifyNoInteractions(sessionService, catalogs);
        }

        @Test
        @DisplayName("HU09 CA02 - Segundo intento fallido: suma uno y tampoco bloquea todavía")
        void deberiaSumarUnIntentoSinBloquearEnElSegundoFallo() {
            // Arrange: ya viene con 1 intento fallido previo
            User user = buildUser(UUID.randomUUID(), UserStatusEnum.ACTIVE, 1);

            // Act
            userStatusService.registerFailedAttempt(user);

            // Assert
            assertThat(user.getFailedAttempts()).isEqualTo((short) 2);
            verify(userRepository, times(1)).save(user);
            verifyNoInteractions(sessionService, catalogs);
        }

        @Test
        @DisplayName("HU02 CA04 / HU09 CA01 - Tercer intento fallido: bloquea al usuario y revoca su sesión")
        void deberiaBloquearAlUsuarioEnElTercerIntentoFallido() {
            // Arrange: ya viene con 2 intentos fallidos previos, este es el tercero
            UUID userId = UUID.randomUUID();
            User user = buildUser(userId, UserStatusEnum.ACTIVE, 2);
            when(catalogs.status(UserStatusEnum.BLOCKED)).thenReturn(buildStatus(UserStatusEnum.BLOCKED));

            // Act
            userStatusService.registerFailedAttempt(user);

            // Assert
            assertThat(user.getFailedAttempts()).isEqualTo((short) 3);
            assertThat(user.getStatus().getCode()).isEqualTo("BLOCKED");
            verify(sessionService).revoke(userId); // un usuario bloqueado no debe conservar sesión activa
            // Un solo guardado: applyStatus solo modifica, y registerFailedAttempt guarda al final
            verify(userRepository, times(1)).save(user);
        }
    }

    @Nested
    @DisplayName("HU08 / HU09 - changeStatus - invalida la sesión al cambiar de estado")
    class ChangeStatus {

        @Test
        @DisplayName("HU08 CA01 - Cambiar a ACTIVE resetea los intentos fallidos y no revoca sesión")
        void deberiaResetearIntentosFallidosAlActivarYNoRevocarSesion() {
            // Arrange
            UUID userId = UUID.randomUUID();
            User user = buildUser(userId, UserStatusEnum.BLOCKED, 3);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(catalogs.status(UserStatusEnum.ACTIVE)).thenReturn(buildStatus(UserStatusEnum.ACTIVE));

            // Act
            userStatusService.changeStatus(userId, UserStatusEnum.ACTIVE);

            // Assert
            assertThat(user.getStatus().getCode()).isEqualTo("ACTIVE");
            assertThat(user.getFailedAttempts()).isZero();
            verifyNoInteractions(sessionService);
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("HU09 CA06 - Cambiar a un estado distinto de ACTIVE revoca la sesión del usuario")
        void deberiaRevocarLaSesionAlCambiarAUnEstadoNoActivo() {
            // Arrange
            UUID userId = UUID.randomUUID();
            User user = buildUser(userId, UserStatusEnum.ACTIVE, 0);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(catalogs.status(UserStatusEnum.INACTIVE)).thenReturn(buildStatus(UserStatusEnum.INACTIVE));

            // Act
            userStatusService.changeStatus(userId, UserStatusEnum.INACTIVE);

            // Assert
            assertThat(user.getStatus().getCode()).isEqualTo("INACTIVE");
            verify(sessionService).revoke(userId);
        }

        @Test
        @DisplayName("HU08 CA03 - Usuario inexistente: rechaza el cambio de estado")
        void deberiaRechazarElCambioCuandoElUsuarioNoExiste() {
            // Arrange
            UUID userId = UUID.randomUUID();
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userStatusService.changeStatus(userId, UserStatusEnum.ACTIVE))
                    .isInstanceOf(UserNotFoundException.class);
            verifyNoInteractions(sessionService, catalogs);
        }
    }

    @Nested
    @DisplayName("HU09 - markInactiveCustomers - trabajo nocturno de inactividad, por lotes")
    class MarkInactiveCustomers {

        private List<UUID> ids(int count) {
            return Stream.generate(UUID::randomUUID).limit(count).toList();
        }

        @Test
        @DisplayName("HU09 CA05 - Sin clientes inactivos no toca la base ni las sesiones")
        void noDeberiaHacerNadaSinClientesInactivos() {
            // Arrange
            when(userRepository.findIdleIds(eq("CUSTOMER"), eq("ACTIVE"), any(LocalDateTime.class)))
                    .thenReturn(List.of());

            // Act
            userStatusService.markInactiveCustomers();

            // Assert
            verify(userRepository, never()).moveStatus(any(), any(), any(), any());
            verifyNoInteractions(sessionService, catalogs);
        }

        @Test
        @DisplayName("HU09 CA03 - Mueve a INACTIVE a los clientes ACTIVE sin actividad en 12 meses y cierra sus sesiones")
        void deberiaInactivarYRevocarSesiones() {
            // Arrange
            List<UUID> idle = ids(3);
            UserStatus active = buildStatus(UserStatusEnum.ACTIVE);
            UserStatus inactive = buildStatus(UserStatusEnum.INACTIVE);
            when(userRepository.findIdleIds(eq("CUSTOMER"), eq("ACTIVE"), any(LocalDateTime.class))).thenReturn(idle);
            when(catalogs.status(UserStatusEnum.ACTIVE)).thenReturn(active);
            when(catalogs.status(UserStatusEnum.INACTIVE)).thenReturn(inactive);

            LocalDateTime before = LocalDateTime.now();

            // Act
            userStatusService.markInactiveCustomers();

            // Assert: el corte es de 12 meses y no se carga ni se guarda ningún usuario
            ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(userRepository).findIdleIds(eq("CUSTOMER"), eq("ACTIVE"), cutoff.capture());
            assertThat(cutoff.getValue()).isBefore(before.minusMonths(12).plusSeconds(5))
                    .isAfter(before.minusMonths(12).minusSeconds(5));
            verify(userRepository).moveStatus(eq(idle), eq(active), eq(inactive), any(LocalDateTime.class));
            verify(sessionService).revokeAll(idle);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Transversal - Procesa por lotes de 500 para no exceder los parámetros de una sentencia")
        void deberiaProcesarPorLotes() {
            // Arrange
            List<UUID> idle = ids(1200);
            when(userRepository.findIdleIds(any(), any(), any())).thenReturn(idle);
            when(catalogs.status(any())).thenAnswer(i -> buildStatus(i.getArgument(0)));

            // Act
            userStatusService.markInactiveCustomers();

            // Assert: 500 + 500 + 200, en orden, sin perder ni repetir ningún id
            ArgumentCaptor<Collection<UUID>> batches = ArgumentCaptor.forClass(Collection.class);
            verify(userRepository, times(3)).moveStatus(batches.capture(), any(), any(), any());
            assertThat(batches.getAllValues()).extracting(Collection::size).containsExactly(500, 500, 200);
            assertThat(batches.getAllValues().stream().flatMap(Collection::stream).toList()).isEqualTo(idle);
            verify(sessionService, times(3)).revokeAll(any());
        }
    }

    @Nested
    @DisplayName("HU08 - requestChange - cambio pedido por un administrador, con matriz de transiciones")
    class RequestChange {

        // Matriz acordada: PENDING_REVIEW -> ACTIVE|BLOCKED, ACTIVE -> INACTIVE|BLOCKED,
        // INACTIVE -> ACTIVE|BLOCKED, BLOCKED -> ACTIVE, PENDING_VERIFICATION -> nada
        private final Map<UserStatusEnum, Set<UserStatusEnum>> expected = Map.of(
                UserStatusEnum.PENDING_VERIFICATION, Set.of(),
                UserStatusEnum.PENDING_REVIEW, Set.of(UserStatusEnum.ACTIVE, UserStatusEnum.BLOCKED),
                UserStatusEnum.ACTIVE, Set.of(UserStatusEnum.INACTIVE, UserStatusEnum.BLOCKED),
                UserStatusEnum.INACTIVE, Set.of(UserStatusEnum.ACTIVE, UserStatusEnum.BLOCKED),
                UserStatusEnum.BLOCKED, Set.of(UserStatusEnum.ACTIVE));

        static Stream<Arguments> everyPair() {
            return Stream.of(UserStatusEnum.values()).flatMap(from ->
                    Stream.of(UserStatusEnum.values()).map(to -> Arguments.of(from, to)));
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("everyPair")
        @DisplayName("HU08 CA01 / HU08 CA04 - Aplica exactamente las transiciones permitidas y rechaza el resto sin tocar al usuario")
        void deberiaAplicarSoloLasTransicionesPermitidas(UserStatusEnum from, UserStatusEnum to) {
            // Arrange
            UUID userId = UUID.randomUUID();
            User user = buildUser(userId, from, 1);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            lenient().when(catalogs.status(to)).thenReturn(buildStatus(to));

            // Act
            if (from == to) {
                userStatusService.requestChange(userId, to);
                // Assert: pedir el estado actual no cambia nada
                assertThat(user.getStatus().getCode()).isEqualTo(from.name());
                assertThat(user.getFailedAttempts()).isEqualTo((short) 1);
                verifyNoInteractions(sessionService);
                verify(userRepository, never()).save(any());
            } else if (expected.get(from).contains(to)) {
                userStatusService.requestChange(userId, to);
                // Assert
                assertThat(user.getStatus().getCode()).isEqualTo(to.name());
                verify(userRepository).save(user);
            } else {
                assertThatThrownBy(() -> userStatusService.requestChange(userId, to))
                        .isInstanceOf(InvalidStatusTransitionException.class)
                        .hasMessageContaining(from.name())
                        .hasMessageContaining(to.name());
                // Assert: nada cambia y no se cierra ninguna sesión
                assertThat(user.getStatus().getCode()).isEqualTo(from.name());
                verifyNoInteractions(sessionService);
                verify(userRepository, never()).save(any());
            }
        }

        @Test
        @DisplayName("HU08 CA03 - Usuario inexistente: rechaza el cambio")
        void deberiaRechazarUnUsuarioInexistente() {
            // Arrange
            UUID userId = UUID.randomUUID();
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userStatusService.requestChange(userId, UserStatusEnum.ACTIVE))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("HU09 CA06 - Bloquear a un usuario ACTIVE revoca su sesión; reactivarlo reinicia los intentos fallidos")
        void deberiaRevocarAlBloquearYReiniciarIntentosAlReactivar() {
            // Arrange
            UUID userId = UUID.randomUUID();
            User user = buildUser(userId, UserStatusEnum.ACTIVE, 2);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(catalogs.status(UserStatusEnum.BLOCKED)).thenReturn(buildStatus(UserStatusEnum.BLOCKED));
            when(catalogs.status(UserStatusEnum.ACTIVE)).thenReturn(buildStatus(UserStatusEnum.ACTIVE));

            // Act
            userStatusService.requestChange(userId, UserStatusEnum.BLOCKED);
            userStatusService.requestChange(userId, UserStatusEnum.ACTIVE);

            // Assert
            verify(sessionService).revoke(userId);
            assertThat(user.getStatus().getCode()).isEqualTo("ACTIVE");
            assertThat(user.getFailedAttempts()).isZero();
        }
    }

}