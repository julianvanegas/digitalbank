package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.AuthSession;
import com.udea.digitalbank.auth.domain.Role;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.repository.AuthSessionRepository;
import com.udea.digitalbank.auth.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private SessionService sessionService;

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

            when(jwtUtil.getExpirationMs()).thenReturn(900_000L); // 15 minutos
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
            assertThat(saved.getExpiresAt()).isCloseTo(before.plusSeconds(900), within(2, ChronoUnit.SECONDS));
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
    @DisplayName("isValid - CA07/CA09/CA10 de HU04 (validez del token frente a la sesión vigente)")
    class IsValid {

        @Test
        @DisplayName("CA07 - Token vigente: la sesión se considera válida")
        void deberiaConsiderarValidaUnaSesionVigente() {
            // Arrange
            UUID userId = UUID.randomUUID();
            String jti = UUID.randomUUID().toString();
            when(sessionRepository.existsByUserIdAndJtiAndExpiresAtAfter(
                    eq(userId), eq(UUID.fromString(jti)), any(LocalDateTime.class)))
                    .thenReturn(true);

            // Act & Assert
            assertThat(sessionService.isValid(userId, jti)).isTrue();
        }

        @Test
        @DisplayName("CA09/CA10 - Sesión cerrada, expirada o con jti distinto: se considera inválida")
        void deberiaConsiderarInvalidaUnaSesionQueNoCoincideOYaExpiro() {
            // Arrange
            UUID userId = UUID.randomUUID();
            String jti = UUID.randomUUID().toString();
            when(sessionRepository.existsByUserIdAndJtiAndExpiresAtAfter(
                    eq(userId), eq(UUID.fromString(jti)), any(LocalDateTime.class)))
                    .thenReturn(false);

            // Act & Assert
            assertThat(sessionService.isValid(userId, jti)).isFalse();
        }

        @Test
        @DisplayName("Un jti nulo se rechaza sin necesidad de consultar la base de datos")
        void deberiaRechazarUnJtiNuloSinConsultarElRepositorio() {
            // Act & Assert
            assertThat(sessionService.isValid(UUID.randomUUID(), null)).isFalse();
            verifyNoInteractions(sessionRepository);
        }
    }
}