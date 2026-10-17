package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.auth.api.CodeCheckResult;
import com.udea.digitalbank.auth.api.VerificationApi;
import com.udea.digitalbank.shared.exception.TooManyRequestsException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotActiveException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.exception.accounts.InsufficientFundsException;
import com.udea.digitalbank.shared.exception.transactions.InvalidReceiverAccountException;
import com.udea.digitalbank.shared.exception.transactions.InvalidTransactionCodeException;
import com.udea.digitalbank.shared.exception.transactions.SameAccountTransferException;
import com.udea.digitalbank.shared.exception.transactions.TransactionCodeExpiredException;
import com.udea.digitalbank.shared.exception.transactions.TransactionNotFoundException;
import com.udea.digitalbank.shared.exception.transactions.TransactionNotPendingException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.dto.TransferRequest;
import com.udea.digitalbank.transactions.mapper.TransactionMapper;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.udea.digitalbank.transactions.TransactionFixtures.RECEIVER_NUMBER;
import static com.udea.digitalbank.transactions.TransactionFixtures.REFERENCE;
import static com.udea.digitalbank.transactions.TransactionFixtures.SENDER_NUMBER;
import static com.udea.digitalbank.transactions.TransactionFixtures.status;
import static com.udea.digitalbank.transactions.TransactionFixtures.transfer;
import static com.udea.digitalbank.transactions.TransactionFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link TransferService}: las dos historias de usuario, un caso por criterio de
 * aceptación. Las dependencias se mockean; el mapper es real.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransferService")
class TransferServiceTest {

    private static final int CODE_TTL_MINUTES = 3;

    private final UUID userId = UUID.randomUUID();
    private final Actor actor = new Actor(userId, false);
    private final AccountView sender = account(SENDER_NUMBER, AccountStatusEnum.ACTIVE, "500.00");
    private final AccountView receiver = account(RECEIVER_NUMBER, AccountStatusEnum.ACTIVE, "20.00");

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionCatalog catalog;
    @Mock
    private TransactionReferenceGenerator referenceGenerator;
    @Mock
    private AccountApi accountApi;
    @Mock
    private VerificationApi verificationApi;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TransferService transferService;

    @BeforeEach
    void setUp() {
        TransactionResponses responses = new TransactionResponses(Mappers.getMapper(TransactionMapper.class), accountApi);
        transferService = new TransferService(transactionRepository, catalog, responses, referenceGenerator, accountApi,
                verificationApi, eventPublisher);
        lenient().when(catalog.status(any())).thenAnswer(i -> status(i.getArgument(0)));
        lenient().when(catalog.type(any())).thenAnswer(i -> type(i.getArgument(0)));
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(verificationApi.transactionCodeTtlMinutes()).thenReturn(CODE_TTL_MINUTES);
    }

    private static AccountView account(String number, AccountStatusEnum status, String balance) {
        return new AccountView(UUID.randomUUID(), "@AN1231", number, UUID.randomUUID(), status.name(),
                new BigDecimal(balance));
    }

    private static TransferRequest request(String from, String to, String amount) {
        TransferRequest request = new TransferRequest();
        request.setSenderAccountNumber(from);
        request.setReceiverAccountNumber(to);
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private void accountsExist() {
        when(accountApi.requireOwnedAccount(userId, SENDER_NUMBER)).thenReturn(sender);
        when(accountApi.getAccountByNumber(RECEIVER_NUMBER)).thenReturn(receiver);
    }

    @Nested
    @DisplayName("initiate - HU1")
    class Initiate {

        @Test
        @DisplayName("CA01 - Cuentas activas y saldo suficiente: queda PENDING, envía el código y responde con el vencimiento")
        void deberiaDejarPendienteLaTransferenciaYEnviarElCodigo() {
            // Arrange
            accountsExist();
            when(referenceGenerator.forTransfer(any(), any())).thenReturn(REFERENCE);
            when(transactionRepository.findPendingOfUserForUpdate(any(), any())).thenReturn(List.of());
            LocalDateTime before = LocalDateTime.now();

            // Act
            TransactionResponse response = transferService.initiate(actor, request(SENDER_NUMBER, RECEIVER_NUMBER, "120.5"));

            // Assert: aún no se mueve dinero; eso ocurre al confirmar con el código
            ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(saved.capture());
            Transaction transaction = saved.getValue();
            assertThat(transaction.is(TransactionStatusEnum.PENDING)).isTrue();
            assertThat(transaction.getUserOriginatorId()).isEqualTo(userId);
            assertThat(transaction.getSenderAccountId()).isEqualTo(sender.id());
            assertThat(transaction.getReceiverAccountId()).isEqualTo(receiver.id());
            assertThat(transaction.getAmount()).isEqualTo(new BigDecimal("120.50"));
            assertThat(transaction.getType().is(TransactionTypeEnum.TRANSFER)).isTrue();

            verify(verificationApi).sendTransactionCode(eq(userId), org.mockito.ArgumentMatchers.contains(REFERENCE));
            verify(accountApi, never()).applyTransfer(any(), any(), any());

            assertThat(response.getReference()).isEqualTo(REFERENCE);
            assertThat(response.getStatus()).isEqualTo("PENDING");
            assertThat(response.getType()).isEqualTo("TRANSFER");
            assertThat(response.getSenderAccountNumber()).isEqualTo(SENDER_NUMBER);
            assertThat(response.getReceiverAccountNumber()).isEqualTo(RECEIVER_NUMBER);
            assertThat(response.getExpiresAt())
                    .isCloseTo(before.plusMinutes(CODE_TTL_MINUTES), within(2, java.time.temporal.ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("El correo del código nombra el monto y la cuenta destino enmascarada, no el número completo")
        void deberiaDescribirLaOperacionEnElCorreoSinElNumeroCompleto() {
            // Arrange
            accountsExist();
            when(referenceGenerator.forTransfer(any(), any())).thenReturn(REFERENCE);

            // Act
            transferService.initiate(actor, request(SENDER_NUMBER, RECEIVER_NUMBER, "120.50"));

            // Assert
            ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
            verify(verificationApi).sendTransactionCode(eq(userId), detail.capture());
            assertThat(detail.getValue()).contains("120.50", "0002", REFERENCE).doesNotContain(RECEIVER_NUMBER);
        }

        @Test
        @DisplayName("Una transferencia pendiente anterior del cliente se rechaza: el código nuevo reemplaza al viejo")
        void deberiaCancelarLaTransferenciaPendienteAnterior() {
            // Arrange
            accountsExist();
            when(referenceGenerator.forTransfer(any(), any())).thenReturn("TRF-20261017-9Bm4TcXeQ2rWnH6kYpJdZs");
            Transaction previous = transfer(userId, sender.id(), receiver.id(), "10.00", TransactionStatusEnum.PENDING);
            when(transactionRepository.findPendingOfUserForUpdate(any(), any())).thenReturn(List.of(previous));

            // Act
            transferService.initiate(actor, request(SENDER_NUMBER, RECEIVER_NUMBER, "30.00"));

            // Assert
            assertThat(previous.is(TransactionStatusEnum.REJECTED)).isTrue();
            verify(transactionRepository).save(previous);
        }

        @Test
        @DisplayName("CA02 - El monto supera el saldo: rechaza por fondos insuficientes y no deja nada")
        void deberiaRechazarPorFondosInsuficientes() {
            // Arrange
            accountsExist();

            // Act + Assert
            assertThatThrownBy(() -> transferService.initiate(actor, request(SENDER_NUMBER, RECEIVER_NUMBER, "500.01")))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("Fondos insuficientes");
            verify(transactionRepository, never()).save(any());
            verifyNoInteractions(verificationApi);
        }

        @Test
        @DisplayName("CA02 - Un monto igual al saldo sí se acepta")
        void deberiaAceptarUnMontoIgualAlSaldo() {
            // Arrange
            accountsExist();
            when(referenceGenerator.forTransfer(any(), any())).thenReturn(REFERENCE);

            // Act
            TransactionResponse response = transferService.initiate(actor, request(SENDER_NUMBER, RECEIVER_NUMBER, "500.00"));

            // Assert
            assertThat(response.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("CA03 - Cuenta destino igual a la de origen: indica que no se puede y no consulta nada")
        void deberiaRechazarUnaTransferenciaALaMismaCuenta() {
            // Act + Assert
            assertThatThrownBy(() -> transferService.initiate(actor, request(SENDER_NUMBER, SENDER_NUMBER, "10.00")))
                    .isInstanceOf(SameAccountTransferException.class)
                    .hasMessage("No se pueden realizar transferencias a la misma cuenta");
            verifyNoInteractions(accountApi, verificationApi);
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("CA04 - Cuenta destino inexistente: informa que no existe")
        void deberiaRechazarUnaCuentaDestinoInexistente() {
            // Arrange
            when(accountApi.requireOwnedAccount(userId, SENDER_NUMBER)).thenReturn(sender);
            when(accountApi.getAccountByNumber(RECEIVER_NUMBER))
                    .thenThrow(new AccountNotFoundException("Cuenta no encontrada: " + RECEIVER_NUMBER));

            // Act + Assert
            assertThatThrownBy(() -> transferService.initiate(actor, request(SENDER_NUMBER, RECEIVER_NUMBER, "10.00")))
                    .isInstanceOf(InvalidReceiverAccountException.class)
                    .hasMessage("La cuenta de destino no existe");
            verify(transactionRepository, never()).save(any());
        }

        @ParameterizedTest
        @CsvSource({"INACTIVE,inactiva", "BLOCKED,bloqueada", "CLOSED,cerrada"})
        @DisplayName("CA04 - Cuenta destino inactiva, bloqueada o cerrada: informa su condición")
        void deberiaInformarLaCondicionDeLaCuentaDestino(AccountStatusEnum status, String word) {
            // Arrange
            when(accountApi.requireOwnedAccount(userId, SENDER_NUMBER)).thenReturn(sender);
            when(accountApi.getAccountByNumber(RECEIVER_NUMBER))
                    .thenReturn(account(RECEIVER_NUMBER, status, "20.00"));

            // Act + Assert
            assertThatThrownBy(() -> transferService.initiate(actor, request(SENDER_NUMBER, RECEIVER_NUMBER, "10.00")))
                    .isInstanceOf(InvalidReceiverAccountException.class)
                    .hasMessageContaining(word);
            verify(transactionRepository, never()).save(any());
        }

        @ParameterizedTest
        @CsvSource({"INACTIVE,inactiva", "BLOCKED,bloqueada", "CLOSED,cerrada"})
        @DisplayName("Cuenta de origen que no está activa: se rechaza antes de mirar el destino")
        void deberiaRechazarUnaCuentaDeOrigenQueNoEstaActiva(AccountStatusEnum status, String word) {
            // Arrange
            when(accountApi.requireOwnedAccount(userId, SENDER_NUMBER))
                    .thenReturn(account(SENDER_NUMBER, status, "500.00"));

            // Act + Assert
            assertThatThrownBy(() -> transferService.initiate(actor, request(SENDER_NUMBER, RECEIVER_NUMBER, "10.00")))
                    .isInstanceOf(AccountNotActiveException.class)
                    .hasMessageContaining(word);
            verify(accountApi, never()).getAccountByNumber(anyString());
        }

        @Test
        @DisplayName("Una cuenta de origen que no es del cliente responde como inexistente")
        void deberiaPropagarLaCuentaDeOrigenAjena() {
            // Arrange
            when(accountApi.requireOwnedAccount(userId, SENDER_NUMBER))
                    .thenThrow(new AccountNotFoundException("Cuenta no encontrada: " + SENDER_NUMBER));

            // Act + Assert
            assertThatThrownBy(() -> transferService.initiate(actor, request(SENDER_NUMBER, RECEIVER_NUMBER, "10.00")))
                    .isInstanceOf(AccountNotFoundException.class);
            verify(transactionRepository, never()).save(any());
            verifyNoInteractions(verificationApi);
        }
    }

    @Nested
    @DisplayName("confirm - HU2")
    class Confirm {

        private Transaction pending() {
            Transaction transaction = transfer(userId, sender.id(), receiver.id(), "120.50",
                    TransactionStatusEnum.PENDING);
            when(transactionRepository.findByReferenceForUpdate(REFERENCE)).thenReturn(Optional.of(transaction));
            return transaction;
        }

        private void numbersAreKnown() {
            when(accountApi.getAccount(sender.id())).thenReturn(sender);
            when(accountApi.getAccount(receiver.id())).thenReturn(receiver);
        }

        @Test
        @DisplayName("CA01 - Código correcto: mueve el dinero, completa la transferencia, publica el comprobante")
        void deberiaMoverElDineroYCompletarConUnCodigoCorrecto() {
            // Arrange
            Transaction transaction = pending();
            numbersAreKnown();
            when(verificationApi.verifyTransactionCode(userId, "123456")).thenReturn(CodeCheckResult.valid());

            // Act
            TransactionResponse response = transferService.confirm(actor, REFERENCE, "123456");

            // Assert
            verify(accountApi).applyTransfer(sender.id(), receiver.id(), new BigDecimal("120.50"));
            assertThat(transaction.is(TransactionStatusEnum.COMPLETED)).isTrue();
            verify(transactionRepository).save(transaction);
            assertThat(response.getStatus()).isEqualTo("COMPLETED");
            assertThat(response.getReference()).isEqualTo(REFERENCE);
            assertThat(response.getSenderAccountNumber()).isEqualTo(SENDER_NUMBER);
            assertThat(response.getReceiverAccountNumber()).isEqualTo(RECEIVER_NUMBER);

            ArgumentCaptor<TransferCompletedEvent> event = ArgumentCaptor.forClass(TransferCompletedEvent.class);
            verify(eventPublisher).publishEvent(event.capture());
            assertThat(event.getValue().userId()).isEqualTo(userId);
            assertThat(event.getValue().reference()).isEqualTo(REFERENCE);
            assertThat(event.getValue().amount()).isEqualByComparingTo("120.50");
        }

        @Test
        @DisplayName("Confirmar una transferencia ya completada devuelve el mismo comprobante sin mover dinero otra vez")
        void deberiaSerIdempotenteSiYaEstaCompletada() {
            // Arrange
            Transaction transaction = transfer(userId, sender.id(), receiver.id(), "120.50",
                    TransactionStatusEnum.COMPLETED);
            when(transactionRepository.findByReferenceForUpdate(REFERENCE)).thenReturn(Optional.of(transaction));
            numbersAreKnown();

            // Act
            TransactionResponse response = transferService.confirm(actor, REFERENCE, "123456");

            // Assert
            assertThat(response.getStatus()).isEqualTo("COMPLETED");
            verifyNoInteractions(verificationApi, eventPublisher);
            verify(accountApi, never()).applyTransfer(any(), any(), any());
        }

        @ParameterizedTest
        @CsvSource({"2,Te quedan 2 intentos", "1,Te queda 1 intento"})
        @DisplayName("CA02 - Código incorrecto en el primer o segundo intento: rechaza, informa los intentos y no procesa")
        void deberiaRechazarUnCodigoIncorrectoSinProcesarLaTransferencia(int remaining, String message) {
            // Arrange
            Transaction transaction = pending();
            when(verificationApi.verifyTransactionCode(userId, "000000")).thenReturn(CodeCheckResult.wrong(remaining));

            // Act + Assert
            assertThatThrownBy(() -> transferService.confirm(actor, REFERENCE, "000000"))
                    .isInstanceOf(InvalidTransactionCodeException.class)
                    .hasMessageContaining(message);
            assertThat(transaction.is(TransactionStatusEnum.PENDING)).isTrue();
            verify(accountApi, never()).applyTransfer(any(), any(), any());
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("CA03 - Tercer fallo: cancela la transferencia en curso y responde que hay que empezar de nuevo")
        void deberiaCancelarLaTransferenciaAlAgotarLosIntentos() {
            // Arrange
            Transaction transaction = pending();
            when(verificationApi.verifyTransactionCode(userId, "000000")).thenReturn(CodeCheckResult.exhausted());

            // Act + Assert
            assertThatThrownBy(() -> transferService.confirm(actor, REFERENCE, "000000"))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessageContaining("cancelada");
            assertThat(transaction.is(TransactionStatusEnum.REJECTED)).isTrue();
            verify(transactionRepository).save(transaction);
            verify(accountApi, never()).applyTransfer(any(), any(), any());
        }

        @Test
        @DisplayName("CA04 - Código vencido: indica que expiró, cancela la transferencia y pide reiniciar")
        void deberiaCancelarLaTransferenciaSiElCodigoExpiro() {
            // Arrange
            Transaction transaction = pending();
            when(verificationApi.verifyTransactionCode(userId, "123456")).thenReturn(CodeCheckResult.expired());

            // Act + Assert
            assertThatThrownBy(() -> transferService.confirm(actor, REFERENCE, "123456"))
                    .isInstanceOf(TransactionCodeExpiredException.class)
                    .hasMessageContaining("expiró")
                    .hasMessageContaining("de nuevo");
            assertThat(transaction.is(TransactionStatusEnum.REJECTED)).isTrue();
            verify(transactionRepository).save(transaction);
            verify(accountApi, never()).applyTransfer(any(), any(), any());
        }

        @Test
        @DisplayName("Si el saldo cambió entre los dos pasos, el código correcto no basta: se rechaza y no hay comprobante")
        void deberiaRechazarSiElSaldoYaNoAlcanzaAlConfirmar() {
            // Arrange
            Transaction transaction = pending();
            when(verificationApi.verifyTransactionCode(userId, "123456")).thenReturn(CodeCheckResult.valid());
            org.mockito.Mockito.doThrow(new InsufficientFundsException("Fondos insuficientes en la cuenta " + SENDER_NUMBER))
                    .when(accountApi).applyTransfer(any(), any(), any());

            // Act + Assert
            assertThatThrownBy(() -> transferService.confirm(actor, REFERENCE, "123456"))
                    .isInstanceOf(InsufficientFundsException.class);
            assertThat(transaction.is(TransactionStatusEnum.REJECTED)).isTrue();
            verify(transactionRepository).save(transaction);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Si una cuenta dejó de estar activa entre los dos pasos, se rechaza con esa causa")
        void deberiaRechazarSiUnaCuentaDejoDeEstarActiva() {
            // Arrange
            Transaction transaction = pending();
            when(verificationApi.verifyTransactionCode(userId, "123456")).thenReturn(CodeCheckResult.valid());
            org.mockito.Mockito.doThrow(new AccountNotActiveException("La cuenta " + RECEIVER_NUMBER + " no está activa"))
                    .when(accountApi).applyTransfer(any(), any(), any());

            // Act + Assert
            assertThatThrownBy(() -> transferService.confirm(actor, REFERENCE, "123456"))
                    .isInstanceOf(AccountNotActiveException.class);
            assertThat(transaction.is(TransactionStatusEnum.REJECTED)).isTrue();
        }

        @Test
        @DisplayName("Una transferencia de otro cliente responde como inexistente y no gasta intentos de su código")
        void deberiaOcultarLaTransferenciaDeOtroCliente() {
            // Arrange
            Transaction foreign = transfer(UUID.randomUUID(), sender.id(), receiver.id(), "10.00",
                    TransactionStatusEnum.PENDING);
            when(transactionRepository.findByReferenceForUpdate(REFERENCE)).thenReturn(Optional.of(foreign));

            // Act + Assert
            assertThatThrownBy(() -> transferService.confirm(actor, REFERENCE, "123456"))
                    .isInstanceOf(TransactionNotFoundException.class);
            verifyNoInteractions(verificationApi, accountApi);
        }

        @Test
        @DisplayName("Una referencia inexistente lanza TransactionNotFoundException")
        void deberiaFallarSiLaReferenciaNoExiste() {
            // Arrange
            when(transactionRepository.findByReferenceForUpdate(REFERENCE)).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> transferService.confirm(actor, REFERENCE, "123456"))
                    .isInstanceOf(TransactionNotFoundException.class);
        }

        @ParameterizedTest
        @CsvSource({"REJECTED,cancelada", "REVERSED,revertida"})
        @DisplayName("Una transferencia ya cancelada o revertida no se puede confirmar")
        void deberiaRechazarUnaTransferenciaQueYaNoEstaPendiente(TransactionStatusEnum current, String word) {
            // Arrange
            Transaction transaction = transfer(userId, sender.id(), receiver.id(), "10.00", current);
            when(transactionRepository.findByReferenceForUpdate(REFERENCE)).thenReturn(Optional.of(transaction));

            // Act + Assert
            assertThatThrownBy(() -> transferService.confirm(actor, REFERENCE, "123456"))
                    .isInstanceOf(TransactionNotPendingException.class)
                    .hasMessageContaining(word);
            verifyNoInteractions(verificationApi);
            verifyNoMoreInteractions(accountApi);
        }
    }

    @Nested
    @DisplayName("rejectExpiredPending")
    class RejectExpiredPending {

        @Test
        @DisplayName("Rechaza las PENDING más viejas que la vigencia del código y devuelve cuántas fueron")
        void deberiaRechazarLasPendientesVencidas() {
            // Arrange
            when(transactionRepository.rejectPendingCreatedBefore(any(), any(), any())).thenReturn(4);
            LocalDateTime before = LocalDateTime.now();

            // Act
            int rejected = transferService.rejectExpiredPending();

            // Assert
            assertThat(rejected).isEqualTo(4);
            ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(transactionRepository).rejectPendingCreatedBefore(cutoff.capture(),
                    org.mockito.ArgumentMatchers.argThat(s -> s.is(TransactionStatusEnum.PENDING)),
                    org.mockito.ArgumentMatchers.argThat(s -> s.is(TransactionStatusEnum.REJECTED)));
            assertThat(cutoff.getValue())
                    .isCloseTo(before.minusMinutes(CODE_TTL_MINUTES), within(2, java.time.temporal.ChronoUnit.SECONDS));
        }
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
