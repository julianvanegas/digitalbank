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
 * Pruebas unitarias (AAA) de {@link TransferInitiationService}: el primer paso de la transferencia, con un caso por cada
 * regla de negocio. Las dependencias externas se mockean; el mapper, {@link TransferParties} y {@link PendingTransfers}
 * son reales.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransferInitiationService - HU20 primer paso: validar y dejar PENDING")
class TransferInitiationServiceTest {

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
    private TransactionReferenceGenerator referenceGenerator;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TransferInitiationService service;

    @BeforeEach
    void setUp() {
        TransactionResponses responses = new TransactionResponses(Mappers.getMapper(TransactionMapper.class), accountApi);
        Clock clock = Clock.systemDefaultZone();
        TransferParties parties = new TransferParties(accountApi, limitService);
        PendingTransfers pendingTransfers = new PendingTransfers(transactionRepository, catalog, accountApi, verificationApi, clock);
        service = new TransferInitiationService(transactionRepository, catalog, responses, referenceGenerator,
                parties, pendingTransfers, verificationApi, clock);
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


    @Test
    @DisplayName("HU20 CA01 / HU21 CA01 - Cuentas activas y saldo suficiente: queda PENDING, envía el código y responde con el vencimiento")
    void deberiaDejarPendienteLaTransferenciaYEnviarElCodigo() {
        // Arrange
        accountsExist();
        when(referenceGenerator.forTransfer(any(), any())).thenReturn(REFERENCE);
        when(transactionRepository.findPendingFromAccountsForUpdate(any(), any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now();

        // Act
        TransactionResponse response = service.initiate(actor, request(SENDER_NUMBER, RECEIVER_NUMBER, "120.5"));

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
    @DisplayName("HU21 CA01 - El correo del código nombra el monto y la cuenta destino enmascarada, no el número completo")
    void deberiaDescribirLaOperacionEnElCorreoSinElNumeroCompleto() {
        // Arrange
        accountsExist();
        when(referenceGenerator.forTransfer(any(), any())).thenReturn(REFERENCE);

        // Act
        service.initiate(actor, request(SENDER_NUMBER, RECEIVER_NUMBER, "120.50"));

        // Assert
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(verificationApi).sendTransactionCode(eq(userId), detail.capture());
        assertThat(detail.getValue()).contains("120.50", "0002", REFERENCE).doesNotContain(RECEIVER_NUMBER);
    }

    @Test
    @DisplayName("Transversal - Una transferencia pendiente anterior del cliente se rechaza: el código nuevo reemplaza al viejo")
    void deberiaCancelarLaTransferenciaPendienteAnterior() {
        // Arrange
        accountsExist();
        when(referenceGenerator.forTransfer(any(), any())).thenReturn("TRF-20261017-9Bm4TcXeQ2rWnH6kYpJdZs");
        Transaction previous = transfer(userId, sender.id(), receiver.id(), "10.00", TransactionStatusEnum.PENDING);
        when(accountApi.getAccountsOfCustomer(sender.customerId())).thenReturn(List.of(sender));
        when(transactionRepository.findPendingFromAccountsForUpdate(eq(List.of(sender.id())), any()))
                .thenReturn(List.of(previous));

        // Act
        service.initiate(actor, request(SENDER_NUMBER, RECEIVER_NUMBER, "30.00"));

        // Assert
        assertThat(previous.is(TransactionStatusEnum.REJECTED)).isTrue();
        verify(transactionRepository).save(previous);
    }

    @Test
    @DisplayName("HU20 CA02 - El monto supera el saldo: rechaza por fondos insuficientes y no deja nada")
    void deberiaRechazarPorFondosInsuficientes() {
        // Arrange
        accountsExist();
        TransferRequest request = request(SENDER_NUMBER, RECEIVER_NUMBER, "500.01");

        // Act + Assert
        assertThatThrownBy(() -> service.initiate(actor, request))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Fondos insuficientes");
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(verificationApi);
    }

    @Test
    @DisplayName("HU20 CA02 - Un monto igual al saldo sí se acepta")
    void deberiaAceptarUnMontoIgualAlSaldo() {
        // Arrange
        accountsExist();
        when(referenceGenerator.forTransfer(any(), any())).thenReturn(REFERENCE);

        // Act
        TransactionResponse response = service.initiate(actor, request(SENDER_NUMBER, RECEIVER_NUMBER, "500.00"));

        // Assert
        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("HU20 CA03 - Cuenta destino igual a la de origen: indica que no se puede y no consulta nada")
    void deberiaRechazarUnaTransferenciaALaMismaCuenta() {
        TransferRequest request = request(SENDER_NUMBER, SENDER_NUMBER, "10.00");
        // Act + Assert
        assertThatThrownBy(() -> service.initiate(actor, request))
                .isInstanceOf(SameAccountTransferException.class)
                .hasMessage("No se pueden realizar transferencias a la misma cuenta");
        verifyNoInteractions(accountApi, verificationApi);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU20 CA04 - Cuenta destino inexistente: informa que no existe")
    void deberiaRechazarUnaCuentaDestinoInexistente() {
        // Arrange
        when(accountApi.requireOwnedAccount(userId, SENDER_NUMBER)).thenReturn(sender);
        when(accountApi.getAccountByNumber(RECEIVER_NUMBER))
                .thenThrow(new AccountNotFoundException("Cuenta no encontrada: " + RECEIVER_NUMBER));
        TransferRequest request = request(SENDER_NUMBER, RECEIVER_NUMBER, "10.00");

        // Act + Assert
        assertThatThrownBy(() -> service.initiate(actor, request))
                .isInstanceOf(InvalidReceiverAccountException.class)
                .hasMessage("La cuenta de destino no existe");
        verify(transactionRepository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"INACTIVE,inactiva", "BLOCKED,bloqueada", "CLOSED,cerrada"})
    @DisplayName("HU20 CA04 - Cuenta destino inactiva, bloqueada o cerrada: informa su condición")
    void deberiaInformarLaCondicionDeLaCuentaDestino(AccountStatusEnum status, String word) {
        // Arrange
        when(accountApi.requireOwnedAccount(userId, SENDER_NUMBER)).thenReturn(sender);
        when(accountApi.getAccountByNumber(RECEIVER_NUMBER))
                .thenReturn(account(RECEIVER_NUMBER, status, "20.00"));
        TransferRequest request = request(SENDER_NUMBER, RECEIVER_NUMBER, "10.00");

        // Act + Assert
        assertThatThrownBy(() -> service.initiate(actor, request))
                .isInstanceOf(InvalidReceiverAccountException.class)
                .hasMessageContaining(word);
        verify(transactionRepository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"INACTIVE,inactiva", "BLOCKED,bloqueada", "CLOSED,cerrada"})
    @DisplayName("HU20 complementario / HU15 CA01 - Cuenta de origen que no está activa: se rechaza antes de mirar el destino")
    void deberiaRechazarUnaCuentaDeOrigenQueNoEstaActiva(AccountStatusEnum status, String word) {
        // Arrange
        when(accountApi.requireOwnedAccount(userId, SENDER_NUMBER))
                .thenReturn(account(SENDER_NUMBER, status, "500.00"));
        TransferRequest request = request(SENDER_NUMBER, RECEIVER_NUMBER, "10.00");

        // Act + Assert
        assertThatThrownBy(() -> service.initiate(actor, request))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining(word);
        verify(accountApi, never()).getAccountByNumber(anyString());
    }

    @Test
    @DisplayName("HU20 complementario - Una cuenta de origen que no es del cliente responde como inexistente")
    void deberiaPropagarLaCuentaDeOrigenAjena() {
        // Arrange
        when(accountApi.requireOwnedAccount(userId, SENDER_NUMBER))
                .thenThrow(new AccountNotFoundException("Cuenta no encontrada: " + SENDER_NUMBER));
        TransferRequest request = request(SENDER_NUMBER, RECEIVER_NUMBER, "10.00");

        // Act + Assert
        assertThatThrownBy(() -> service.initiate(actor, request))
                .isInstanceOf(AccountNotFoundException.class);
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(verificationApi);
    }

    @Test
    @DisplayName("Topes - Un cliente se valida contra los topes de su cuenta de origen con el monto de la transferencia")
    void deberiaValidarLosTopesDelClienteAlIniciar() {
        // Arrange
        accountsExist();
        when(referenceGenerator.forTransfer(any(), any())).thenReturn(REFERENCE);

        // Act
        service.initiate(actor, request(SENDER_NUMBER, RECEIVER_NUMBER, "120.50"));

        // Assert
        verify(limitService).requireWithinLimits(sender, TransactionTypeEnum.TRANSFER, new BigDecimal("120.50"));
    }

    @Test
    @DisplayName("Topes - Un cliente que supera su tope no deja transacción, no envía el código y no cancela la pendiente anterior")
    void deberiaRechazarAlClienteQueSuperaElTope() {
        // Arrange
        accountsExist();
        org.mockito.Mockito.doThrow(new TransactionLimitExceededException("El monto supera el acumulado diario"))
                .when(limitService).requireWithinLimits(any(), any(), any());
        TransferRequest request = request(SENDER_NUMBER, RECEIVER_NUMBER, "120.50");

        // Act + Assert
        assertThatThrownBy(() -> service.initiate(actor, request))
                .isInstanceOf(TransactionLimitExceededException.class);
        verify(transactionRepository, never()).save(any());
        verify(transactionRepository, never()).findPendingFromAccountsForUpdate(any(), any());
        verifyNoInteractions(verificationApi);
    }

    @Test
    @DisplayName("Admin en nombre del cliente - Opera sobre la cuenta de cualquier cliente, sin topes, y el código va al titular")
    void deberiaTransferirEnNombreDelClienteSinTopesYEnviarleElCodigoAlTitular() {
        // Arrange
        UUID adminId = UUID.randomUUID();
        UUID holderId = UUID.randomUUID();
        Actor admin = new Actor(adminId, true);
        // Una cuenta con saldo de sobra para un monto que un cliente no podría transferir de golpe
        AccountView rich = account(SENDER_NUMBER, AccountStatusEnum.ACTIVE, "90000000.00");
        when(accountApi.getAccountByNumber(SENDER_NUMBER)).thenReturn(rich);
        when(accountApi.getAccountByNumber(RECEIVER_NUMBER)).thenReturn(receiver);
        when(accountApi.getOwnerUserId(rich.id())).thenReturn(holderId);
        when(referenceGenerator.forTransfer(any(), any())).thenReturn(REFERENCE);

        // Act
        TransactionResponse response = service.initiate(admin, request(SENDER_NUMBER, RECEIVER_NUMBER, "85000000"));

        // Assert: la ordena el admin, el código es del titular y los topes no se consultan
        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getUserOriginatorId()).isEqualTo(adminId);
        assertThat(saved.getValue().getSenderAccountId()).isEqualTo(rich.id());
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(verificationApi).sendTransactionCode(eq(holderId), detail.capture());
        assertThat(detail.getValue()).contains("empleado", REFERENCE);
        verify(verificationApi, never()).sendTransactionCode(eq(adminId), anyString());
        verify(accountApi, never()).requireOwnedAccount(any(), anyString());
        verifyNoInteractions(limitService);
        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Admin en nombre del cliente - Una cuenta de origen inexistente responde como no encontrada")
    void deberiaPropagarLaCuentaDeOrigenInexistenteAlAdmin() {
        // Arrange
        when(accountApi.getAccountByNumber(SENDER_NUMBER))
                .thenThrow(new AccountNotFoundException("Cuenta no encontrada: " + SENDER_NUMBER));
        TransferRequest request = request(SENDER_NUMBER, RECEIVER_NUMBER, "10.00");
        Actor admin = new Actor(UUID.randomUUID(), true);

        // Act + Assert
        assertThatThrownBy(() -> service.initiate(admin, request))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(verificationApi);
    }

    @Test
    @DisplayName("Admin en nombre del cliente - Cancela la pendiente del titular aunque la hubiera ordenado el propio cliente")
    void deberiaCancelarLaPendienteDelTitularAlOrdenarUnAdmin() {
        // Arrange
        Actor admin = new Actor(UUID.randomUUID(), true);
        when(accountApi.getAccountByNumber(SENDER_NUMBER)).thenReturn(sender);
        when(accountApi.getAccountByNumber(RECEIVER_NUMBER)).thenReturn(receiver);
        when(accountApi.getOwnerUserId(sender.id())).thenReturn(userId);
        when(referenceGenerator.forTransfer(any(), any())).thenReturn("TRF-20261017-9Bm4TcXeQ2rWnH6kYpJdZs");
        when(accountApi.getAccountsOfCustomer(sender.customerId())).thenReturn(List.of(sender));
        Transaction ownPending = transfer(userId, sender.id(), receiver.id(), "10.00", TransactionStatusEnum.PENDING);
        when(transactionRepository.findPendingFromAccountsForUpdate(eq(List.of(sender.id())), any()))
                .thenReturn(List.of(ownPending));

        // Act
        service.initiate(admin, request(SENDER_NUMBER, RECEIVER_NUMBER, "30.00"));

        // Assert
        assertThat(ownPending.is(TransactionStatusEnum.REJECTED)).isTrue();
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
