package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.AuthSession;
import com.udea.digitalbank.auth.domain.Role;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.repository.AuthSessionRepository;
import com.udea.digitalbank.auth.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link SessionService} (HU04 Gestionar sesión).
 * Se mockean AuthSessionRepository y JwtUtil: no se firma un JWT real ni se toca una base de datos.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private AuthSessionRepository sessionRepository;
    @Mock
    private JwtUtil jwtUtil;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository, jwtUtil, 5);
    }

    private static Role buildRole(String code) {
        Role role = new Role();
        setField(role, "id", (short) 1);
        setField(role, "code", code);
        return role;
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
    @DisplayName("start - CA01 de HU04 (creación de sesión al completar la autenticación)")
    class Start {

        @Test
        @DisplayName("Abre la sesión (sobrescribiendo cualquier anterior) y devuelve el token firmado")
        void deberiaAbrirLaSesionYDevolverElToken() {
            // Arrange
            UUID userId = UUID.randomUUID();
            String jti = UUID.randomUUID().toString();
            User user = new User();
            user.setId(userId);
            user.setRole(buildRole("CUSTOMER"));

            when(jwtUtil.getExpirationMs()).thenReturn(3_600_000L); // tope del token: 1 hora
            when(jwtUtil.generateJti()).thenReturn(jti);
            when(jwtUtil.generateToken(eq(userId), eq("CUSTOMER"), eq(jti), any(Date.class), any(Date.class)))
                    .thenReturn("token-firmado");

            LocalDateTime before = LocalDateTime.now();

            // Act
            String token = sessionService.start(user);

            // Assert
            assertThat(token).isEqualTo("token-firmado");
            ArgumentCaptor<AuthSession> captor = ArgumentCaptor.forClass(AuthSession.class);
            // La sesión única se garantiza guardando con la PK = userId: cualquier fila anterior
            // del mismo usuario queda sobrescrita por el propio UPSERT/merge de JPA.
            verify(sessionRepository).save(captor.capture());
            AuthSession saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getJti()).isEqualTo(UUID.fromString(jti));
            assertThat(saved.getIssuedAt()).isCloseTo(before, within(2, ChronoUnit.SECONDS));
            assertThat(saved.getExpiresAt()).isCloseTo(before.plusMinutes(5), within(2, ChronoUnit.SECONDS));

            // El token lleva como exp el tope de 1 hora, independiente de la inactividad
            ArgumentCaptor<Date> tokenExp = ArgumentCaptor.forClass(Date.class);
            verify(jwtUtil).generateToken(eq(userId), eq("CUSTOMER"), eq(jti), any(Date.class), tokenExp.capture());
            assertThat(tokenExp.getValue().getTime())
                    .isCloseTo(System.currentTimeMillis() + 3_600_000L, within(2_000L));
        }
    }

    @Nested
    @DisplayName("revoke - CA02 de HU04 (cerrar sesión manualmente / logout)")
    class Revoke {

        @Test
        @DisplayName("Elimina la sesión activa del usuario")
        void deberiaEliminarLaSesionDelUsuario() {
            // Arrange
            UUID userId = UUID.randomUUID();

            // Act
            sessionService.revoke(userId);

            // Assert
            verify(sessionRepository).deleteByUser(userId);
        }
    }

    @Nested
    @DisplayName("validateAndTouch - CA07/CA09/CA10 de HU04 (validez del token e inactividad)")
    class ValidateAndTouch {

        @Test
        @DisplayName("CA07 - Sesión vigente y activa: es válida y la petición renueva la actividad")
        void deberiaConsiderarValidaUnaSesionVigenteYRenovarLaActividad() {
            // Arrange
            UUID userId = UUID.randomUUID();
            String jti = UUID.randomUUID().toString();
            when(sessionRepository.touch(eq(userId), eq(UUID.fromString(jti)),
                    any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(1);

            LocalDateTime before = LocalDateTime.now();

            // Act & Assert
            assertThat(sessionService.validateAndTouch(userId, jti)).isTrue();

            // El nuevo vencimiento es "ahora" más 5 minutos de inactividad
            ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> newExpiresAt = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(sessionRepository).touch(eq(userId), eq(UUID.fromString(jti)), now.capture(), newExpiresAt.capture());
            assertThat(now.getValue()).isCloseTo(before, within(2, ChronoUnit.SECONDS));
            assertThat(newExpiresAt.getValue()).isEqualTo(now.getValue().plusMinutes(5));
        }

        @Test
        @DisplayName("CA09/CA10 - Sesión cerrada, expirada, inactiva o con jti distinto: es inválida")
        void deberiaConsiderarInvalidaUnaSesionQueNoCoincideExpiroOEstaInactiva() {
            // Arrange
            UUID userId = UUID.randomUUID();
            String jti = UUID.randomUUID().toString();
            when(sessionRepository.touch(eq(userId), eq(UUID.fromString(jti)),
                    any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(0);

            // Act & Assert
            assertThat(sessionService.validateAndTouch(userId, jti)).isFalse();
        }

        @Test
        @DisplayName("Un jti nulo o mal formado se rechaza sin necesidad de consultar la base de datos")
        void deberiaRechazarUnJtiNuloOMalFormadoSinConsultarElRepositorio() {
            // Act & Assert
            assertThat(sessionService.validateAndTouch(UUID.randomUUID(), null)).isFalse();
            assertThat(sessionService.validateAndTouch(UUID.randomUUID(), "no-es-un-uuid")).isFalse();
            verifyNoInteractions(sessionRepository);
        }
    }
}
