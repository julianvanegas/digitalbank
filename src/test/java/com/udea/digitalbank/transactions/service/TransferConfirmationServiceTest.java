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
import com.udea.digitalbank.shared.exception.transactions.TransactionLimitExceededException;
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
import java.time.Clock;
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
 * Pruebas unitarias (AAA) de {@link TransferConfirmationService}: el segundo paso de la transferencia, con un caso por
 * cada regla de negocio. Las dependencias externas se mockean; el mapper, {@link TransferParties} y
 * {@link PendingTransfers} son reales.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransferConfirmationService - HU21 segundo paso: verificar el código")
class TransferConfirmationServiceTest {

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
    private TransactionLimitService limitService;
    @Mock
    private AccountApi accountApi;
    @Mock
    private VerificationApi verificationApi;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TransferConfirmationService service;

    @BeforeEach
    void setUp() {
        TransactionResponses responses = new TransactionResponses(Mappers.getMapper(TransactionMapper.class), accountApi);
        Clock clock = Clock.systemDefaultZone();
        TransferParties parties = new TransferParties(accountApi, limitService);
        PendingTransfers pendingTransfers = new PendingTransfers(transactionRepository, catalog, accountApi, verificationApi, clock);
        service = new TransferConfirmationService(transactionRepository, catalog, responses, parties,
                pendingTransfers, accountApi, verificationApi, eventPublisher);
        lenient().when(catalog.status(any())).thenAnswer(i -> status(i.getArgument(0)));
        lenient().when(catalog.type(any())).thenAnswer(i -> type(i.getArgument(0)));
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(verificationApi.transactionCodeTtlMinutes()).thenReturn(CODE_TTL_MINUTES);
    }

    private static AccountView account(String number, AccountStatusEnum status, String balance) {
        return new AccountView(UUID.randomUUID(), "@AN1231", number, UUID.randomUUID(), "SAVINGS",
                status.name(), new BigDecimal(balance));
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
    @DisplayName("HU21 CA01 / HU20 CA01 - Código correcto: mueve el dinero, completa la transferencia, publica el comprobante")
    void deberiaMoverElDineroYCompletarConUnCodigoCorrecto() {
        // Arrange
        Transaction transaction = pending();
        numbersAreKnown();
        when(verificationApi.verifyTransactionCode(userId, "123456")).thenReturn(CodeCheckResult.valid());

        // Act
        TransactionResponse response = service.confirm(actor, REFERENCE, "123456");

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
    @DisplayName("HU21 CA01 - Confirmar una transferencia ya completada devuelve el mismo comprobante sin mover dinero otra vez")
    void deberiaSerIdempotenteSiYaEstaCompletada() {
        // Arrange
        Transaction transaction = transfer(userId, sender.id(), receiver.id(), "120.50",
                TransactionStatusEnum.COMPLETED);
        when(transactionRepository.findByReferenceForUpdate(REFERENCE)).thenReturn(Optional.of(transaction));
        numbersAreKnown();

        // Act
        TransactionResponse response = service.confirm(actor, REFERENCE, "123456");

        // Assert
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        verifyNoInteractions(verificationApi, eventPublisher);
        verify(accountApi, never()).applyTransfer(any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"2,Te quedan 2 intentos", "1,Te queda 1 intento"})
    @DisplayName("HU21 CA02 - Código incorrecto en el primer o segundo intento: rechaza, informa los intentos y no procesa")
    void deberiaRechazarUnCodigoIncorrectoSinProcesarLaTransferencia(int remaining, String message) {
        // Arrange
        Transaction transaction = pending();
        when(verificationApi.verifyTransactionCode(userId, "000000")).thenReturn(CodeCheckResult.wrong(remaining));

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(actor, REFERENCE, "000000"))
                .isInstanceOf(InvalidTransactionCodeException.class)
                .hasMessageContaining(message);
        assertThat(transaction.is(TransactionStatusEnum.PENDING)).isTrue();
        verify(accountApi, never()).applyTransfer(any(), any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU21 CA03 - Tercer fallo: cancela la transferencia en curso y responde que se debe empezar de nuevo")
    void deberiaCancelarLaTransferenciaAlAgotarLosIntentos() {
        // Arrange
        Transaction transaction = pending();
        when(verificationApi.verifyTransactionCode(userId, "000000")).thenReturn(CodeCheckResult.exhausted());

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(actor, REFERENCE, "000000"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("cancelada");
        assertThat(transaction.is(TransactionStatusEnum.REJECTED)).isTrue();
        verify(transactionRepository).save(transaction);
        verify(accountApi, never()).applyTransfer(any(), any(), any());
    }

    @Test
    @DisplayName("HU21 CA04 - Código vencido: indica que expiró, cancela la transferencia y pide reiniciar")
    void deberiaCancelarLaTransferenciaSiElCodigoExpiro() {
        // Arrange
        Transaction transaction = pending();
        when(verificationApi.verifyTransactionCode(userId, "123456")).thenReturn(CodeCheckResult.expired());

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(actor, REFERENCE, "123456"))
                .isInstanceOf(TransactionCodeExpiredException.class)
                .hasMessageContaining("expiró")
                .hasMessageContaining("de nuevo");
        assertThat(transaction.is(TransactionStatusEnum.REJECTED)).isTrue();
        verify(transactionRepository).save(transaction);
        verify(accountApi, never()).applyTransfer(any(), any(), any());
    }

    @Test
    @DisplayName("HU20 CA02 - Si el saldo cambió entre los dos pasos, el código correcto no basta: se rechaza y no hay comprobante")
    void deberiaRechazarSiElSaldoYaNoAlcanzaAlConfirmar() {
        // Arrange
        Transaction transaction = pending();
        when(verificationApi.verifyTransactionCode(userId, "123456")).thenReturn(CodeCheckResult.valid());
        org.mockito.Mockito.doThrow(new InsufficientFundsException("Fondos insuficientes en la cuenta " + SENDER_NUMBER))
                .when(accountApi).applyTransfer(any(), any(), any());

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(actor, REFERENCE, "123456"))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(transaction.is(TransactionStatusEnum.REJECTED)).isTrue();
        verify(transactionRepository).save(transaction);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("HU20 CA04 - Si una cuenta dejó de estar activa entre los dos pasos, se rechaza con esa causa")
    void deberiaRechazarSiUnaCuentaDejoDeEstarActiva() {
        // Arrange
        Transaction transaction = pending();
        when(verificationApi.verifyTransactionCode(userId, "123456")).thenReturn(CodeCheckResult.valid());
        org.mockito.Mockito.doThrow(new AccountNotActiveException("La cuenta " + RECEIVER_NUMBER + " no está activa"))
                .when(accountApi).applyTransfer(any(), any(), any());

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(actor, REFERENCE, "123456"))
                .isInstanceOf(AccountNotActiveException.class);
        assertThat(transaction.is(TransactionStatusEnum.REJECTED)).isTrue();
    }

    @Test
    @DisplayName("Transversal - Una transferencia de otro cliente responde como inexistente y no gasta intentos de su código")
    void deberiaOcultarLaTransferenciaDeOtroCliente() {
        // Arrange
        Transaction foreign = transfer(UUID.randomUUID(), sender.id(), receiver.id(), "10.00",
                TransactionStatusEnum.PENDING);
        when(transactionRepository.findByReferenceForUpdate(REFERENCE)).thenReturn(Optional.of(foreign));

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(actor, REFERENCE, "123456"))
                .isInstanceOf(TransactionNotFoundException.class);
        verifyNoInteractions(verificationApi, accountApi);
    }

    @Test
    @DisplayName("Transversal - Una referencia inexistente lanza TransactionNotFoundException")
    void deberiaFallarSiLaReferenciaNoExiste() {
        // Arrange
        when(transactionRepository.findByReferenceForUpdate(REFERENCE)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(actor, REFERENCE, "123456"))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @ParameterizedTest
    @CsvSource({"REJECTED,cancelada", "REVERSED,revertida"})
    @DisplayName("HU21 CA03 - Una transferencia ya cancelada o revertida no se puede confirmar")
    void deberiaRechazarUnaTransferenciaQueYaNoEstaPendiente(TransactionStatusEnum current, String word) {
        // Arrange
        Transaction transaction = transfer(userId, sender.id(), receiver.id(), "10.00", current);
        when(transactionRepository.findByReferenceForUpdate(REFERENCE)).thenReturn(Optional.of(transaction));

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(actor, REFERENCE, "123456"))
                .isInstanceOf(TransactionNotPendingException.class)
                .hasMessageContaining(word);
        verifyNoInteractions(verificationApi);
        verifyNoMoreInteractions(accountApi);
    }

    @Test
    @DisplayName("Topes - Al confirmar se revalidan los topes del cliente con el monto de la transferencia")
    void deberiaRevalidarLosTopesAlConfirmar() {
        // Arrange
        Transaction transaction = pending();
        numbersAreKnown();
        when(verificationApi.verifyTransactionCode(userId, "123456")).thenReturn(CodeCheckResult.valid());

        // Act
        service.confirm(actor, REFERENCE, "123456");

        // Assert
        verify(limitService).requireWithinLimits(sender, TransactionTypeEnum.TRANSFER, transaction.getAmount());
    }

    @Test
    @DisplayName("Topes - Si el consumo del día ya no deja espacio al confirmar, se rechaza y no se mueve dinero")
    void deberiaRechazarSiElTopeYaNoAlcanzaAlConfirmar() {
        // Arrange
        Transaction transaction = pending();
        when(accountApi.getAccount(sender.id())).thenReturn(sender);
        when(verificationApi.verifyTransactionCode(userId, "123456")).thenReturn(CodeCheckResult.valid());
        org.mockito.Mockito.doThrow(new TransactionLimitExceededException("El monto supera el acumulado diario"))
                .when(limitService).requireWithinLimits(any(), any(), any());

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(actor, REFERENCE, "123456"))
                .isInstanceOf(TransactionLimitExceededException.class);
        assertThat(transaction.is(TransactionStatusEnum.REJECTED)).isTrue();
        verify(transactionRepository).save(transaction);
        verify(accountApi, never()).applyTransfer(any(), any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Admin en nombre del cliente - Confirma con el código del titular, sin topes, y el comprobante va al titular")
    void deberiaConfirmarConElCodigoDelTitularSinTopes() {
        // Arrange
        UUID adminId = UUID.randomUUID();
        UUID holderId = UUID.randomUUID();
        Actor admin = new Actor(adminId, true);
        Transaction transaction = transfer(adminId, sender.id(), receiver.id(), "9000000.00",
                TransactionStatusEnum.PENDING);
        when(transactionRepository.findByReferenceForUpdate(REFERENCE)).thenReturn(Optional.of(transaction));
        when(accountApi.getOwnerUserId(sender.id())).thenReturn(holderId);
        numbersAreKnown();
        when(verificationApi.verifyTransactionCode(holderId, "123456")).thenReturn(CodeCheckResult.valid());

        // Act
        TransactionResponse response = service.confirm(admin, REFERENCE, "123456");

        // Assert
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        verify(accountApi).applyTransfer(sender.id(), receiver.id(), new BigDecimal("9000000.00"));
        verifyNoInteractions(limitService);
        verify(verificationApi, never()).verifyTransactionCode(eq(adminId), anyString());
        ArgumentCaptor<TransferCompletedEvent> event = ArgumentCaptor.forClass(TransferCompletedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().userId()).isEqualTo(holderId);
    }

    @Test
    @DisplayName("Admin en nombre del cliente - Un código incorrecto del titular deja la transferencia pendiente")
    void deberiaRechazarElCodigoIncorrectoDelTitularSinProcesar() {
        // Arrange
        UUID adminId = UUID.randomUUID();
        UUID holderId = UUID.randomUUID();
        Transaction transaction = transfer(adminId, sender.id(), receiver.id(), "10.00",
                TransactionStatusEnum.PENDING);
        when(transactionRepository.findByReferenceForUpdate(REFERENCE)).thenReturn(Optional.of(transaction));
        when(accountApi.getOwnerUserId(sender.id())).thenReturn(holderId);
        when(verificationApi.verifyTransactionCode(holderId, "000000")).thenReturn(CodeCheckResult.wrong(2));
        Actor admin = new Actor(adminId, true);

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(admin, REFERENCE, "000000"))
                .isInstanceOf(InvalidTransactionCodeException.class);
        assertThat(transaction.is(TransactionStatusEnum.PENDING)).isTrue();
        verify(accountApi, never()).applyTransfer(any(), any(), any());
    }

    @Test
    @DisplayName("Admin en nombre del cliente - Un admin no confirma la transferencia que inició otro usuario")
    void deberiaOcultarLaTransferenciaDeOtroOrdenanteAlAdmin() {
        // Arrange
        Transaction foreign = transfer(UUID.randomUUID(), sender.id(), receiver.id(), "10.00",
                TransactionStatusEnum.PENDING);
        when(transactionRepository.findByReferenceForUpdate(REFERENCE)).thenReturn(Optional.of(foreign));
        Actor admin = new Actor(UUID.randomUUID(), true);

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(admin, REFERENCE, "123456"))
                .isInstanceOf(TransactionNotFoundException.class);
        verifyNoInteractions(verificationApi);
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
