package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.CodeCheckResult;
import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.auth.repository.VerificationChallengeRepository;
import com.udea.digitalbank.shared.exception.TooManyRequestsException;
import com.udea.digitalbank.shared.exception.auth.InvalidVerificationCodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/**
 * Pruebas de flujo (AAA) de {@link VerificationService}: encadenan varias operaciones sobre el servicio real, con el
 * HMAC real de {@link CodeHasher} y la tabla de retos en memoria ({@link ChallengeStoreFake}). Verifican los criterios
 * que dependen de la secuencia: fallar y reintentar, reutilizar un código, reenviar y vencer.
 * <p>
 * El vencimiento se simula moviendo la fecha de expiración del reto, porque el servicio usa LocalDateTime.now().
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Flujos de verificación por código (2FA, recuperación y transacción)")
class VerificationFlowTest {

    private static final String EMAIL = "maria.lopez@example.com";
    private static final String INVALID_MESSAGE = "Código de verificación inválido o expirado";

    @Mock
    private VerificationChallengeRepository challengeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private Catalogs catalogs;

    private ChallengeStoreFake store;
    private User user;

    @BeforeEach
    void setUp() {
        store = new ChallengeStoreFake(challengeRepository);
        for (PurposeEnum purpose : PurposeEnum.values()) {
            lenient().when(catalogs.purpose(purpose)).thenReturn(AuthFixtures.purpose(purpose));
        }
        user = AuthFixtures.customer(EMAIL, "hash", UserStatusEnum.ACTIVE);
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    /** Servicio con el intervalo mínimo entre reenvíos indicado, en segundos. */
    private VerificationService service(long resendIntervalSeconds) {
        return new VerificationService(challengeRepository, userRepository, catalogs,
                new CodeHasher("secreto-de-prueba-para-el-hmac"), resendIntervalSeconds);
    }

    /** Un código de 6 dígitos distinto del correcto. */
    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Nested
    @DisplayName("HU03 - Verificar identidad mediante 2FA")
    class TwoFactor {

        @Test
        @DisplayName("HU03 CA02 / HU03 CA06 - El código correcto se acepta una sola vez: reutilizarlo se rechaza")
        void deberiaAceptarElCodigoUnaSolaVez() {
            // Arrange
            VerificationService service = service(0);
            IssuedChallenge issued = service.issue(user, PurposeEnum.LOGIN);

            // Act
            UUID verified = service.verifyById(issued.challengeId(), PurposeEnum.LOGIN, issued.code());

            // Assert
            assertThat(verified).isEqualTo(user.getId());
            assertThat(store.get(issued.challengeId())).isNull();
            assertThatThrownBy(() -> service.verifyById(issued.challengeId(), PurposeEnum.LOGIN, issued.code()))
                    .isInstanceOf(InvalidVerificationCodeException.class);
        }

        @Test
        @DisplayName("HU03 CA03 - Un código incorrecto se rechaza, registra el intento y el código correcto sigue sirviendo")
        void deberiaRegistrarElIntentoYConservarElReto() {
            // Arrange
            VerificationService service = service(0);
            IssuedChallenge issued = service.issue(user, PurposeEnum.LOGIN);

            // Act
            Throwable error = org.assertj.core.api.Assertions.catchThrowable(() ->
                    service.verifyById(issued.challengeId(), PurposeEnum.LOGIN, wrong(issued.code())));

            // Assert
            assertThat(error).isInstanceOf(InvalidVerificationCodeException.class).hasMessage(INVALID_MESSAGE);
            assertThat(store.get(issued.challengeId()).getFailedAttempts()).isEqualTo((short) 1);
            assertThat(service.verifyById(issued.challengeId(), PurposeEnum.LOGIN, issued.code()))
                    .isEqualTo(user.getId());
        }

        @Test
        @DisplayName("HU03 CA04 - Un código vencido se rechaza con el mensaje genérico y el reto se elimina")
        void deberiaRechazarUnCodigoVencido() {
            // Arrange
            VerificationService service = service(0);
            IssuedChallenge issued = service.issue(user, PurposeEnum.LOGIN);
            store.expire(issued.challengeId());

            // Act + Assert
            assertThatThrownBy(() -> service.verifyById(issued.challengeId(), PurposeEnum.LOGIN, issued.code()))
                    .isInstanceOf(InvalidVerificationCodeException.class)
                    .hasMessage(INVALID_MESSAGE);
            assertThat(store.get(issued.challengeId())).isNull();
        }

        @Test
        @DisplayName("HU03 CA05 - Con 3 códigos incorrectos el código se invalida: el correcto ya no sirve")
        void deberiaInvalidarElCodigoAlTercerFallo() {
            // Arrange
            VerificationService service = service(0);
            IssuedChallenge issued = service.issue(user, PurposeEnum.LOGIN);
            String bad = wrong(issued.code());

            // Act
            for (int attempt = 1; attempt <= 3; attempt++) {
                assertThatThrownBy(() -> service.verifyById(issued.challengeId(), PurposeEnum.LOGIN, bad))
                        .isInstanceOf(InvalidVerificationCodeException.class);
            }

            // Assert
            assertThat(store.get(issued.challengeId())).isNull();
            assertThatThrownBy(() -> service.verifyById(issued.challengeId(), PurposeEnum.LOGIN, issued.code()))
                    .isInstanceOf(InvalidVerificationCodeException.class);
        }

        @Test
        @DisplayName("HU03 CA07 - El reenvío genera un código nuevo y el anterior deja de ser válido")
        void deberiaInvalidarElCodigoAnteriorAlReenviar() {
            // Arrange
            VerificationService service = service(0);
            IssuedChallenge first = service.issue(user, PurposeEnum.LOGIN);

            // Act
            ResentChallenge resent = service.resend(first.challengeId(), PurposeEnum.LOGIN);

            // Assert
            assertThat(resent.challenge().challengeId()).isEqualTo(first.challengeId());
            if (!resent.challenge().code().equals(first.code())) {
                assertThatThrownBy(() -> service.verifyById(first.challengeId(), PurposeEnum.LOGIN, first.code()))
                        .isInstanceOf(InvalidVerificationCodeException.class);
            }
            assertThat(service.verifyById(first.challengeId(), PurposeEnum.LOGIN, resent.challenge().code()))
                    .isEqualTo(user.getId());
        }

        @Test
        @DisplayName("HU03 CA07 - Cada reenvío cuenta dentro de los 3 intentos: el tercer reenvío se rechaza con 429")
        void deberiaLimitarLosReenvios() {
            // Arrange
            VerificationService service = service(0);
            IssuedChallenge issued = service.issue(user, PurposeEnum.LOGIN);

            // Act
            ResentChallenge firstResend = service.resend(issued.challengeId(), PurposeEnum.LOGIN);
            ResentChallenge secondResend = service.resend(issued.challengeId(), PurposeEnum.LOGIN);

            // Assert
            assertThat(firstResend.remainingResends()).isEqualTo(1);
            assertThat(secondResend.remainingResends()).isZero();
            assertThatThrownBy(() -> service.resend(issued.challengeId(), PurposeEnum.LOGIN))
                    .isInstanceOf(TooManyRequestsException.class);
        }

        @Test
        @DisplayName("HU03 CA07 - Con el código vencido no se puede reenviar: hay que iniciar sesión de nuevo")
        void noDeberiaReenviarUnCodigoVencido() {
            // Arrange
            VerificationService service = service(0);
            IssuedChallenge issued = service.issue(user, PurposeEnum.LOGIN);
            store.expire(issued.challengeId());

            // Act + Assert
            assertThatThrownBy(() -> service.resend(issued.challengeId(), PurposeEnum.LOGIN))
                    .isInstanceOf(InvalidVerificationCodeException.class);
        }

        @Test
        @DisplayName("HU03 CA07 - Antes de 60 segundos desde el último código, el reenvío se rechaza con 429")
        void deberiaExigirElIntervaloMinimoEntreReenvios() {
            // Arrange
            VerificationService service = service(60);
            IssuedChallenge issued = service.issue(user, PurposeEnum.LOGIN);

            // Act
            Throwable error = org.assertj.core.api.Assertions.catchThrowable(() ->
                    service.resend(issued.challengeId(), PurposeEnum.LOGIN));

            // Assert
            assertThat(error).isInstanceOf(TooManyRequestsException.class);
            assertThat(((TooManyRequestsException) error).getRetryAfterSeconds()).isPositive();
        }

        @Test
        @DisplayName("HU03 CA05 / HU03 CA07 - Iniciar sesión de nuevo emite un reto nuevo y reemplaza el anterior")
        void deberiaReemplazarElRetoAlIniciarSesionDeNuevo() {
            // Arrange
            VerificationService service = service(0);
            IssuedChallenge first = service.issue(user, PurposeEnum.LOGIN);

            // Act
            IssuedChallenge second = service.issue(user, PurposeEnum.LOGIN);

            // Assert
            assertThat(store.get(first.challengeId())).isNull();
            assertThat(store.size()).isEqualTo(1);
            assertThatThrownBy(() -> service.verifyById(first.challengeId(), PurposeEnum.LOGIN, first.code()))
                    .isInstanceOf(InvalidVerificationCodeException.class);
            assertThat(service.verifyById(second.challengeId(), PurposeEnum.LOGIN, second.code()))
                    .isEqualTo(user.getId());
        }

        @Test
        @DisplayName("HU03 CA08 - Un código de inicio de sesión no sirve para otro propósito")
        void noDeberiaAceptarElCodigoParaOtroProposito() {
            // Arrange
            VerificationService service = service(0);
            IssuedChallenge issued = service.issue(user, PurposeEnum.LOGIN);

            // Act + Assert
            assertThatThrownBy(() -> service.verifyByEmail(EMAIL, PurposeEnum.PASSWORD_RESET, issued.code()))
                    .isInstanceOf(InvalidVerificationCodeException.class);
            assertThat(store.get(issued.challengeId())).isNotNull();
        }
    }

    @Nested
    @DisplayName("HU05 - Recuperar contraseña")
    class PasswordRecovery {

        @Test
        @DisplayName("HU05 CA03 / HU05 CA11 - El código de recuperación se acepta una sola vez")
        void deberiaAceptarElCodigoDeRecuperacionUnaSolaVez() {
            // Arrange
            VerificationService service = service(0);
            IssuedChallenge issued = service.issue(user, PurposeEnum.PASSWORD_RESET);

            // Act
            UUID verified = service.verifyByEmail(EMAIL, PurposeEnum.PASSWORD_RESET, issued.code());

            // Assert
            assertThat(verified).isEqualTo(user.getId());
            assertThatThrownBy(() -> service.verifyByEmail(EMAIL, PurposeEnum.PASSWORD_RESET, issued.code()))
                    .isInstanceOf(InvalidVerificationCodeException.class);
        }

        @Test
        @DisplayName("HU05 CA04 / HU05 CA05 - Dos fallos se registran; el tercero invalida el código")
        void deberiaInvalidarElCodigoDeRecuperacionAlTercerFallo() {
            // Arrange
            VerificationService service = service(0);
            IssuedChallenge issued = service.issue(user, PurposeEnum.PASSWORD_RESET);
            String bad = wrong(issued.code());

            // Act
            assertThatThrownBy(() -> service.verifyByEmail(EMAIL, PurposeEnum.PASSWORD_RESET, bad))
                    .isInstanceOf(InvalidVerificationCodeException.class);
            assertThatThrownBy(() -> service.verifyByEmail(EMAIL, PurposeEnum.PASSWORD_RESET, bad))
                    .isInstanceOf(InvalidVerificationCodeException.class);
            short afterTwo = store.get(issued.challengeId()).getFailedAttempts();
            assertThatThrownBy(() -> service.verifyByEmail(EMAIL, PurposeEnum.PASSWORD_RESET, bad))
                    .isInstanceOf(InvalidVerificationCodeException.class);

            // Assert
            assertThat(afterTwo).isEqualTo((short) 2);
            assertThat(store.get(issued.challengeId())).isNull();
            assertThatThrownBy(() -> service.verifyByEmail(EMAIL, PurposeEnum.PASSWORD_RESET, issued.code()))
                    .isInstanceOf(InvalidVerificationCodeException.class);
        }

        @Test
        @DisplayName("HU05 CA06 - Un código de recuperación vencido se rechaza con el mensaje genérico")
        void deberiaRechazarUnCodigoDeRecuperacionVencido() {
            // Arrange
            VerificationService service = service(0);
            IssuedChallenge issued = service.issue(user, PurposeEnum.PASSWORD_RESET);
            store.expire(issued.challengeId());

            // Act + Assert
            assertThatThrownBy(() -> service.verifyByEmail(EMAIL, PurposeEnum.PASSWORD_RESET, issued.code()))
                    .isInstanceOf(InvalidVerificationCodeException.class)
                    .hasMessage(INVALID_MESSAGE);
        }

        @Test
        @DisplayName("HU05 CA07 - Una nueva solicitud genera otro código y el anterior deja de ser válido")
        void deberiaReemplazarElCodigoConUnaNuevaSolicitud() {
            // Arrange
            VerificationService service = service(0);
            IssuedChallenge first = service.issueThrottled(user, PurposeEnum.PASSWORD_RESET).orElseThrow();

            // Act
            Optional<IssuedChallenge> second = service.issueThrottled(user, PurposeEnum.PASSWORD_RESET);

            // Assert
            assertThat(second).isPresent();
            assertThat(store.get(first.challengeId())).isNull();
            assertThat(service.verifyByEmail(EMAIL, PurposeEnum.PASSWORD_RESET, second.get().code()))
                    .isEqualTo(user.getId());
        }

        @Test
        @DisplayName("HU05 CA07 - Dentro del intervalo mínimo no se emite otro código y el vigente sigue sirviendo")
        void noDeberiaEmitirOtroCodigoDentroDelIntervalo() {
            // Arrange
            VerificationService service = service(60);
            IssuedChallenge first = service.issueThrottled(user, PurposeEnum.PASSWORD_RESET).orElseThrow();

            // Act
            Optional<IssuedChallenge> second = service.issueThrottled(user, PurposeEnum.PASSWORD_RESET);

            // Assert
            assertThat(second).isEmpty();
            assertThat(service.verifyByEmail(EMAIL, PurposeEnum.PASSWORD_RESET, first.code()))
                    .isEqualTo(user.getId());
        }
    }

    @Nested
    @DisplayName("HU21 - Verificar transacción mediante 2FA")
    class TransactionCode {

        @Test
        @DisplayName("HU21 CA01 / HU21 CA02 - Dos códigos incorrectos dejan el código vigente y el correcto lo valida")
        void deberiaValidarElCodigoCorrectoTrasDosFallos() {
            // Arrange
            VerificationService service = service(0);
            IssuedChallenge issued = service.issue(user, PurposeEnum.TRANSACTION);
            String bad = wrong(issued.code());

            // Act
            CodeCheckResult first = service.check(user.getId(), PurposeEnum.TRANSACTION, bad);
            CodeCheckResult second = service.check(user.getId(), PurposeEnum.TRANSACTION, bad);
            CodeCheckResult third = service.check(user.getId(), PurposeEnum.TRANSACTION, issued.code());

            // Assert
            assertThat(first).isEqualTo(CodeCheckResult.wrong(2));
            assertThat(second).isEqualTo(CodeCheckResult.wrong(1));
            assertThat(third.outcome()).isEqualTo(CodeCheckResult.Outcome.VALID);
        }

        @Test
        @DisplayName("HU21 CA03 - El tercer código incorrecto agota los intentos y el correcto ya no sirve")
        void deberiaAgotarLosIntentosAlTercerFallo() {
            // Arrange
            VerificationService service = service(0);
            IssuedChallenge issued = service.issue(user, PurposeEnum.TRANSACTION);
            String bad = wrong(issued.code());
            service.check(user.getId(), PurposeEnum.TRANSACTION, bad);
            service.check(user.getId(), PurposeEnum.TRANSACTION, bad);

            // Act
            CodeCheckResult third = service.check(user.getId(), PurposeEnum.TRANSACTION, bad);

            // Assert
            assertThat(third.outcome()).isEqualTo(CodeCheckResult.Outcome.EXHAUSTED);
            assertThat(service.check(user.getId(), PurposeEnum.TRANSACTION, issued.code()).outcome())
                    .isNotEqualTo(CodeCheckResult.Outcome.VALID);
        }

        @Test
        @DisplayName("HU21 CA04 - El código de transacción vence a los 3 minutos y entonces responde EXPIRED")
        void deberiaVencerElCodigoDeTransaccion() {
            // Arrange
            VerificationService service = service(0);
            IssuedChallenge issued = service.issue(user, PurposeEnum.TRANSACTION);
            store.expire(issued.challengeId());

            // Act
            CodeCheckResult result = service.check(user.getId(), PurposeEnum.TRANSACTION, issued.code());

            // Assert
            assertThat(issued.ttlMinutes()).isEqualTo(3);
            assertThat(result.outcome()).isEqualTo(CodeCheckResult.Outcome.EXPIRED);
        }
    }
}
