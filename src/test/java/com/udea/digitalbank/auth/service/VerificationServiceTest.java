package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.ChallengePurpose;
import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.domain.VerificationChallenge;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.auth.repository.VerificationChallengeRepository;
import com.udea.digitalbank.shared.exception.auth.InvalidVerificationCodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link VerificationService}: emisión y consumo de códigos de verificación,
 * usados tanto por HU03 (2FA de login) como por HU05 (recuperación de contraseña).
 *
 * Nota metodológica: el servicio usa {@code LocalDateTime.now()} directamente (no un {@code Clock}
 * inyectado), así que los límites exactos de 5 minutos (CA03/CA04 de HU03, CA04/CA05 de HU05) se
 * verifican de forma relativa (justo antes/después de "ahora" al momento del test) en vez de con un
 * reloj congelado. Si más adelante refactorizan el servicio para recibir un {@code Clock}, esas
 * pruebas se pueden volver exactas.
 */
@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    private static final long RESEND_INTERVAL_SECONDS = 30L;

    @Mock
    private VerificationChallengeRepository challengeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private Catalogs catalogs;
    @Mock
    private CodeHasher codeHasher;

    private VerificationService verificationService;

    @BeforeEach
    void setUp() {
        // Constructor explícito (en vez de @InjectMocks) para poder fijar resendIntervalSeconds,
        // que @InjectMocks dejaría en 0 al no tener un mock que inyectar en ese parámetro primitivo.
        verificationService = new VerificationService(
                challengeRepository, userRepository, catalogs, codeHasher, RESEND_INTERVAL_SECONDS);
    }

    private static ChallengePurpose buildPurpose(short id, String code, int ttlMinutes, int maxAttempts) {
        ChallengePurpose purpose = new ChallengePurpose();
        setField(purpose, "id", id);
        setField(purpose, "code", code);
        setField(purpose, "ttlMinutes", ttlMinutes);
        setField(purpose, "maxAttempts", maxAttempts);
        return purpose;
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

    private static VerificationChallenge buildChallenge(UUID id, Long userId, ChallengePurpose purpose,
                                                        String codeHash, LocalDateTime expiresAt,
                                                        int failedAttempts) {
        VerificationChallenge challenge = new VerificationChallenge();
        challenge.setId(id);
        challenge.setUserId(userId);
        challenge.setPurpose(purpose);
        challenge.setCodeHash(codeHash);
        challenge.setExpiresAt(expiresAt);
        challenge.setFailedAttempts(failedAttempts);
        return challenge;
    }

    @Nested
    @DisplayName("issue - CA01 de HU03 (parte de: generar y preparar el envío del código)")
    class Issue {

        @Test
        @DisplayName("Genera un reto nuevo con la vigencia del propósito y elimina el anterior")
        void deberiaGenerarUnRetoConLaVigenciaDelProposito() {
            // Arrange
            User user = new User();
            user.setId(7L);
            ChallengePurpose purpose = buildPurpose((short) 1, "LOGIN", 5, 3);
            when(catalogs.purpose(PurposeEnum.LOGIN)).thenReturn(purpose);
            when(codeHasher.hash(eq(7L), eq((short) 1), anyString())).thenReturn("hash-simulado");

            LocalDateTime before = LocalDateTime.now();

            // Act
            IssuedChallenge issued = verificationService.issue(user, PurposeEnum.LOGIN);

            // Assert
            verify(challengeRepository).deleteByUserAndPurpose(7L, "LOGIN");
            ArgumentCaptor<VerificationChallenge> captor = ArgumentCaptor.forClass(VerificationChallenge.class);
            verify(challengeRepository).save(captor.capture());
            VerificationChallenge saved = captor.getValue();

            assertThat(saved.getUserId()).isEqualTo(7L);
            assertThat(saved.getCodeHash()).isEqualTo("hash-simulado");
            assertThat(saved.getExpiresAt()).isCloseTo(before.plusMinutes(5), within(2, java.time.temporal.ChronoUnit.SECONDS));
            assertThat(issued.code()).matches("\\d{6}"); // código numérico de 6 dígitos
            assertThat(issued.ttlMinutes()).isEqualTo(5);
            assertThat(issued.challengeId()).isEqualTo(saved.getId());
        }
    }

    @Nested
    @DisplayName("verifyById - HU03 CA02/CA04/CA05/CA06/CA07/CA08/CA09")
    class VerifyById {

        private final ChallengePurpose loginPurpose = buildPurpose((short) 1, "LOGIN", 5, 3);

        @Test
        @DisplayName("CA02/CA08 - Código correcto y vigente: se acepta y se invalida para reutilizarlo (uso único)")
        void deberiaAceptarUnCodigoCorrectoYVigente() {
            // Arrange
            UUID challengeId = UUID.randomUUID();
            VerificationChallenge challenge = buildChallenge(challengeId, 42L, loginPurpose,
                    "hash-esperado", LocalDateTime.now().plusMinutes(2), 0);
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.of(challenge));
            when(codeHasher.matches(42L, (short) 1, "123456", "hash-esperado")).thenReturn(true);

            // Act
            Long userId = verificationService.verifyById(challengeId, PurposeEnum.LOGIN, "123456");

            // Assert
            assertThat(userId).isEqualTo(42L);
            verify(challengeRepository).delete(challenge); // CA09: al consumirse, queda inválido para un futuro uso
            verify(challengeRepository, never()).save(any());
        }

        @Test
        @DisplayName("CA04 - Código expirado: se rechaza y el reto se elimina")
        void deberiaRechazarUnCodigoExpirado() {
            // Arrange
            UUID challengeId = UUID.randomUUID();
            VerificationChallenge challenge = buildChallenge(challengeId, 42L, loginPurpose,
                    "hash-esperado", LocalDateTime.now().minusSeconds(1), 0); // ya vencido
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.of(challenge));

            // Act
            Throwable thrown = catchThrowable(() -> verificationService.verifyById(challengeId, PurposeEnum.LOGIN, "123456"));

            // Assert
            assertThat(thrown).isInstanceOf(InvalidVerificationCodeException.class);
            verify(challengeRepository).delete(challenge);
            verifyNoInteractions(codeHasher); // ni siquiera se compara el código si ya expiró
        }

        @Test
        @DisplayName("CA05 - Código incorrecto sin alcanzar el límite: registra el intento y conserva el reto")
        void deberiaRegistrarElIntentoSinInvalidarElRetoAunNoAlcanzaElLimite() {
            // Arrange: ya tiene 1 intento fallido, el límite son 3
            UUID challengeId = UUID.randomUUID();
            VerificationChallenge challenge = buildChallenge(challengeId, 42L, loginPurpose,
                    "hash-esperado", LocalDateTime.now().plusMinutes(2), 1);
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.of(challenge));
            when(codeHasher.matches(42L, (short) 1, "000000", "hash-esperado")).thenReturn(false);

            // Act
            Throwable thrown = catchThrowable(() -> verificationService.verifyById(challengeId, PurposeEnum.LOGIN, "000000"));

            // Assert
            assertThat(thrown).isInstanceOf(InvalidVerificationCodeException.class);
            assertThat(challenge.getFailedAttempts()).isEqualTo(2);
            verify(challengeRepository).save(challenge);
            verify(challengeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("CA06 - Tercer intento incorrecto: invalida el reto en vez de conservarlo")
        void deberiaInvalidarElRetoEnElTercerIntentoIncorrecto() {
            // Arrange: ya tiene 2 intentos fallidos, este sería el tercero
            UUID challengeId = UUID.randomUUID();
            VerificationChallenge challenge = buildChallenge(challengeId, 42L, loginPurpose,
                    "hash-esperado", LocalDateTime.now().plusMinutes(2), 2);
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.of(challenge));
            when(codeHasher.matches(42L, (short) 1, "000000", "hash-esperado")).thenReturn(false);

            // Act
            Throwable thrown = catchThrowable(() -> verificationService.verifyById(challengeId, PurposeEnum.LOGIN, "000000"));

            // Assert
            assertThat(thrown).isInstanceOf(InvalidVerificationCodeException.class);
            verify(challengeRepository).delete(challenge);
            verify(challengeRepository, never()).save(any());
        }

        @Test
        @DisplayName("CA07 - Intentar con un reto ya invalidado/inexistente: se rechaza de inmediato")
        void deberiaRechazarCuandoElRetoYaNoExiste() {
            // Arrange
            UUID challengeId = UUID.randomUUID();
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> verificationService.verifyById(challengeId, PurposeEnum.LOGIN, "123456"))
                    .isInstanceOf(InvalidVerificationCodeException.class);
            verifyNoInteractions(codeHasher);
        }

        private Throwable catchThrowable(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
            return org.assertj.core.api.Assertions.catchThrowable(callable);
        }
    }

    @Nested
    @DisplayName("verifyByEmail - HU05 Recuperar contraseña")
    class VerifyByEmail {

        private final ChallengePurpose resetPurpose = buildPurpose((short) 3, "PASSWORD_RESET", 5, 3);

        @Test
        @DisplayName("CA (mensaje genérico) - Correo no registrado: mismo rechazo que un código inválido")
        void deberiaRechazarConElMismoMensajeGenericoCuandoElCorreoNoExiste() {
            // Arrange
            when(userRepository.findByEmail("noexiste@example.com")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> verificationService.verifyByEmail("noexiste@example.com", PurposeEnum.PASSWORD_RESET, "123456"))
                    .isInstanceOf(InvalidVerificationCodeException.class);
            verifyNoInteractions(codeHasher);
        }

        @Test
        @DisplayName("CA03 - Código de recuperación válido: se acepta y permite continuar")
        void deberiaAceptarUnCodigoDeRecuperacionValido() {
            // Arrange
            User user = new User();
            user.setId(9L);
            user.setEmail("cliente@example.com");
            UUID challengeId = UUID.randomUUID();
            VerificationChallenge challenge = buildChallenge(challengeId, 9L, resetPurpose,
                    "hash-esperado", LocalDateTime.now().plusMinutes(3), 0);

            when(userRepository.findByEmail("cliente@example.com")).thenReturn(Optional.of(user));
            when(challengeRepository.lockByUserAndPurpose(9L, "PASSWORD_RESET")).thenReturn(Optional.of(challenge));
            when(codeHasher.matches(9L, (short) 3, "654321", "hash-esperado")).thenReturn(true);

            // Act
            Long userId = verificationService.verifyByEmail("cliente@example.com", PurposeEnum.PASSWORD_RESET, "654321");

            // Assert
            assertThat(userId).isEqualTo(9L);
            verify(challengeRepository).delete(challenge);
        }
    }
}