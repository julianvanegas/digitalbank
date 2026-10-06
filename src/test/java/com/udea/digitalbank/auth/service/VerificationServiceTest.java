package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.ChallengePurpose;
import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.domain.VerificationChallenge;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.auth.repository.VerificationChallengeRepository;
import com.udea.digitalbank.shared.exception.auth.InvalidVerificationCodeException;
import com.udea.digitalbank.shared.exception.TooManyRequestsException;
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
        setField(purpose, "purpose", code);
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

    private static VerificationChallenge buildChallenge(UUID id, UUID userId, ChallengePurpose purpose,
                                                        String codeHash, LocalDateTime expiresAt,
                                                        int failedAttempts) {
        VerificationChallenge challenge = new VerificationChallenge();
        challenge.setId(id);
        challenge.setUserId(userId);
        challenge.setPurpose(purpose);
        challenge.setCodeHash(codeHash);
        challenge.setExpiresAt(expiresAt);
        challenge.setFailedAttempts((short) failedAttempts);
        return challenge;
    }

    @Nested
    @DisplayName("issue - CA01 de HU03 (parte de: generar y preparar el envío del código)")
    class Issue {

        @Test
        @DisplayName("Genera un reto nuevo con la vigencia del propósito y elimina el anterior")
        void deberiaGenerarUnRetoConLaVigenciaDelProposito() {
            // Arrange
            UUID userId = UUID.randomUUID();
            User user = new User();
            user.setId(userId);
            ChallengePurpose purpose = buildPurpose((short) 1, "LOGIN", 5, 3);
            when(catalogs.purpose(PurposeEnum.LOGIN)).thenReturn(purpose);
            when(codeHasher.hash(eq(userId), eq((short) 1), anyString())).thenReturn("hash-simulado");

            LocalDateTime before = LocalDateTime.now();

            // Act
            IssuedChallenge issued = verificationService.issue(user, PurposeEnum.LOGIN);

            // Assert
            verify(challengeRepository).deleteByUserAndPurpose(userId, "LOGIN");
            ArgumentCaptor<VerificationChallenge> captor = ArgumentCaptor.forClass(VerificationChallenge.class);
            verify(challengeRepository).save(captor.capture());
            VerificationChallenge saved = captor.getValue();

            assertThat(saved.getUserId()).isEqualTo(userId);
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
        private final UUID userId = UUID.randomUUID();

        @Test
        @DisplayName("CA02/CA08 - Código correcto y vigente: se acepta y se invalida para reutilizarlo (uso único)")
        void deberiaAceptarUnCodigoCorrectoYVigente() {
            // Arrange
            UUID challengeId = UUID.randomUUID();
            VerificationChallenge challenge = buildChallenge(challengeId, userId, loginPurpose,
                    "hash-esperado", LocalDateTime.now().plusMinutes(2), 0);
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.of(challenge));
            when(codeHasher.matches(userId, (short) 1, "123456", "hash-esperado")).thenReturn(true);

            // Act
            UUID verifiedUserId = verificationService.verifyById(challengeId, PurposeEnum.LOGIN, "123456");

            // Assert
            assertThat(verifiedUserId).isEqualTo(userId);
            verify(challengeRepository).delete(challenge); // CA09: al consumirse, queda inválido para un futuro uso
            verify(challengeRepository, never()).save(any());
        }

        @Test
        @DisplayName("CA04 - Código expirado: se rechaza y el reto se elimina")
        void deberiaRechazarUnCodigoExpirado() {
            // Arrange
            UUID challengeId = UUID.randomUUID();
            VerificationChallenge challenge = buildChallenge(challengeId, userId, loginPurpose,
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
            VerificationChallenge challenge = buildChallenge(challengeId, userId, loginPurpose,
                    "hash-esperado", LocalDateTime.now().plusMinutes(2), 1);
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.of(challenge));
            when(codeHasher.matches(userId, (short) 1, "000000", "hash-esperado")).thenReturn(false);

            // Act
            Throwable thrown = catchThrowable(() -> verificationService.verifyById(challengeId, PurposeEnum.LOGIN, "000000"));

            // Assert
            assertThat(thrown).isInstanceOf(InvalidVerificationCodeException.class);
            assertThat(challenge.getFailedAttempts()).isEqualTo((short) 2);
            verify(challengeRepository).save(challenge);
            verify(challengeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("CA06 - Tercer intento incorrecto: invalida el reto en vez de conservarlo")
        void deberiaInvalidarElRetoEnElTercerIntentoIncorrecto() {
            // Arrange: ya tiene 2 intentos fallidos, este sería el tercero
            UUID challengeId = UUID.randomUUID();
            VerificationChallenge challenge = buildChallenge(challengeId, userId, loginPurpose,
                    "hash-esperado", LocalDateTime.now().plusMinutes(2), 2);
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.of(challenge));
            when(codeHasher.matches(userId, (short) 1, "000000", "hash-esperado")).thenReturn(false);

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
            UUID userId = UUID.randomUUID();
            User user = new User();
            user.setId(userId);
            user.setEmail("cliente@example.com");
            UUID challengeId = UUID.randomUUID();
            VerificationChallenge challenge = buildChallenge(challengeId, userId, resetPurpose,
                    "hash-esperado", LocalDateTime.now().plusMinutes(3), 0);

            when(userRepository.findByEmail("cliente@example.com")).thenReturn(Optional.of(user));
            when(challengeRepository.lockByUserAndPurpose(userId, "PASSWORD_RESET")).thenReturn(Optional.of(challenge));
            when(codeHasher.matches(userId, (short) 3, "654321", "hash-esperado")).thenReturn(true);

            // Act
            UUID verifiedUserId = verificationService.verifyByEmail("cliente@example.com", PurposeEnum.PASSWORD_RESET, "654321");

            // Assert
            assertThat(verifiedUserId).isEqualTo(userId);
            verify(challengeRepository).delete(challenge);
        }
    }

    @Nested
    @DisplayName("resend - reenvío del código 2FA: cada reenvío gasta un intento del propósito")
    class Resend {

        private final ChallengePurpose loginPurpose = buildPurpose((short) 1, "LOGIN", 5, 3);
        private final UUID userId = UUID.randomUUID();
        private final UUID challengeId = UUID.randomUUID();

        // Reto emitido hace secondsAgo segundos: la emisión se deriva de expiresAt menos la vigencia del propósito
        private VerificationChallenge challengeIssuedAgo(long secondsAgo, int failedAttempts) {
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5).minusSeconds(secondsAgo);
            return buildChallenge(challengeId, userId, loginPurpose, "hash-viejo", expiresAt, failedAttempts);
        }

        @Test
        @DisplayName("Reemite en el sitio: mismo id, código nuevo, vigencia renovada y un intento gastado")
        void deberiaReemitirElRetoEnElSitio() {
            // Arrange
            VerificationChallenge challenge = challengeIssuedAgo(60, 0);
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.of(challenge));
            when(codeHasher.hash(eq(userId), eq((short) 1), anyString())).thenReturn("hash-nuevo");
            LocalDateTime before = LocalDateTime.now();

            // Act
            ResentChallenge resent = verificationService.resend(challengeId, PurposeEnum.LOGIN);

            // Assert
            assertThat(resent.userId()).isEqualTo(userId);
            assertThat(resent.challenge().challengeId()).isEqualTo(challengeId);
            assertThat(resent.challenge().code()).matches("\\d{6}");
            assertThat(resent.challenge().ttlMinutes()).isEqualTo(5);
            assertThat(challenge.getCodeHash()).isEqualTo("hash-nuevo");
            assertThat(challenge.getFailedAttempts()).isEqualTo((short) 1);
            assertThat(challenge.getExpiresAt())
                    .isCloseTo(before.plusMinutes(5), within(2, java.time.temporal.ChronoUnit.SECONDS));
            verify(challengeRepository).save(challenge);
            verify(challengeRepository, never()).delete(any());
            verify(challengeRepository, never()).deleteByUserAndPurpose(any(), anyString());
        }

        @Test
        @DisplayName("El hash guardado corresponde al código que se devuelve para enviar")
        void deberiaGuardarElHashDelCodigoEnviado() {
            // Arrange
            VerificationChallenge challenge = challengeIssuedAgo(60, 0);
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.of(challenge));
            when(codeHasher.hash(eq(userId), eq((short) 1), anyString())).thenReturn("hash-nuevo");

            // Act
            ResentChallenge resent = verificationService.resend(challengeId, PurposeEnum.LOGIN);

            // Assert
            verify(codeHasher).hash(userId, (short) 1, resent.challenge().code());
        }

        @Test
        @DisplayName("Informa cuántos reenvíos quedan y cuánto esperar para el siguiente")
        void deberiaInformarReenviosRestantesYEspera() {
            // Arrange: max_attempts = 3, sin gastos previos -> tras reenviar quedan 1 reenvío
            VerificationChallenge challenge = challengeIssuedAgo(60, 0);
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.of(challenge));
            when(codeHasher.hash(any(), anyShort(), anyString())).thenReturn("hash-nuevo");

            // Act
            ResentChallenge resent = verificationService.resend(challengeId, PurposeEnum.LOGIN);

            // Assert
            assertThat(resent.remainingResends()).isEqualTo(1);
            assertThat(resent.retryAfterSeconds()).isEqualTo(RESEND_INTERVAL_SECONDS);
        }

        @Test
        @DisplayName("Conserva los fallos acumulados: reenviar no devuelve los intentos")
        void noDeberiaReiniciarLosIntentosFallidos() {
            // Arrange: ya hubo 1 fallo; el reenvío suma otro y deja 0 reenvíos
            VerificationChallenge challenge = challengeIssuedAgo(60, 1);
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.of(challenge));
            when(codeHasher.hash(any(), anyShort(), anyString())).thenReturn("hash-nuevo");

            // Act
            ResentChallenge resent = verificationService.resend(challengeId, PurposeEnum.LOGIN);

            // Assert
            assertThat(challenge.getFailedAttempts()).isEqualTo((short) 2);
            assertThat(resent.remainingResends()).isZero();
        }

        @Test
        @DisplayName("Reto desconocido o de otro propósito: error genérico")
        void deberiaRechazarUnRetoDesconocido() {
            // Arrange
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> verificationService.resend(challengeId, PurposeEnum.LOGIN))
                    .isInstanceOf(InvalidVerificationCodeException.class);
            verify(challengeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Reto vencido: error genérico y no se reemite")
        void deberiaRechazarUnRetoVencido() {
            // Arrange
            VerificationChallenge challenge = buildChallenge(challengeId, userId, loginPurpose,
                    "hash-viejo", LocalDateTime.now().minusSeconds(1), 0);
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.of(challenge));

            // Act + Assert
            assertThatThrownBy(() -> verificationService.resend(challengeId, PurposeEnum.LOGIN))
                    .isInstanceOf(InvalidVerificationCodeException.class);
            verify(challengeRepository, never()).save(any());
            verifyNoInteractions(codeHasher);
        }

        @Test
        @DisplayName("Antes del intervalo mínimo: 429 con los segundos que faltan y sin reemitir")
        void deberiaRechazarUnReenvioAntesDelIntervalo() {
            // Arrange: emitido hace 10 s, el intervalo es de 30 s -> faltan unos 20 s
            VerificationChallenge challenge = challengeIssuedAgo(10, 0);
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.of(challenge));

            // Act + Assert
            assertThatThrownBy(() -> verificationService.resend(challengeId, PurposeEnum.LOGIN))
                    .isInstanceOfSatisfying(TooManyRequestsException.class,
                            e -> assertThat(e.getRetryAfterSeconds()).isBetween(19L, 20L));
            assertThat(challenge.getFailedAttempts()).isZero();
            assertThat(challenge.getCodeHash()).isEqualTo("hash-viejo");
            verify(challengeRepository, never()).save(any());
            verifyNoInteractions(codeHasher);
        }

        @Test
        @DisplayName("Sin reenvíos disponibles: 429 sin tiempo de espera y sin reemitir")
        void deberiaRechazarCuandoYaNoQuedanReenvios() {
            // Arrange: con 2 gastados, un reenvío más dejaría el código nuevo sin intentos
            VerificationChallenge challenge = challengeIssuedAgo(60, 2);
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.of(challenge));

            // Act + Assert
            assertThatThrownBy(() -> verificationService.resend(challengeId, PurposeEnum.LOGIN))
                    .isInstanceOfSatisfying(TooManyRequestsException.class,
                            e -> assertThat(e.getRetryAfterSeconds()).isZero());
            assertThat(challenge.getFailedAttempts()).isEqualTo((short) 2);
            verify(challengeRepository, never()).save(any());
            verifyNoInteractions(codeHasher);
        }

        @Test
        @DisplayName("El tope manda sobre el intervalo: sin reenvíos no se pide esperar")
        void elTopeTienePrioridadSobreElIntervalo() {
            // Arrange: sin reenvíos y además recién emitido
            VerificationChallenge challenge = challengeIssuedAgo(0, 2);
            when(challengeRepository.lockByIdAndPurpose(challengeId, "LOGIN")).thenReturn(Optional.of(challenge));

            // Act + Assert
            assertThatThrownBy(() -> verificationService.resend(challengeId, PurposeEnum.LOGIN))
                    .isInstanceOfSatisfying(TooManyRequestsException.class,
                            e -> assertThat(e.getRetryAfterSeconds()).isZero());
        }
    }

}
