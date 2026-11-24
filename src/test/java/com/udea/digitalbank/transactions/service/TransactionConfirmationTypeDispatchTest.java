package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.auth.api.CodeCheckResult;
import com.udea.digitalbank.auth.api.VerificationApi;
import com.udea.digitalbank.shared.exception.InvalidRequestException;
import com.udea.digitalbank.shared.exception.transactions.TransactionNotFoundException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.mapper.TransactionMapper;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) del despacho por tipo en {@link TransactionConfirmationService#confirm}: la misma ruta
 * confirma transferencias y retiros, cada uno con su operación; un tipo sin operación (el depósito, que se hace en un
 * paso) se rechaza antes de verificar el código, sin gastar un intento del cliente. Se monta con
 * TransferParties y PendingTransfers reales sobre los mocks.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU19 - La confirmación elige la operación por el tipo de la transacción")
class TransactionConfirmationTypeDispatchTest {

    private static final String WITHDRAWAL_REFERENCE = "RET-20261017-7Xk2Pq9RmZ4tNvB8cHdWeA";
    private static final String DEPOSIT_REFERENCE = "DEP-20261017-7Xk2Pq9RmZ4tNvB8cHdWeA";
    private static final String CODE = "123456";

    private final UUID userId = UUID.randomUUID();
    private final Actor actor = new Actor(userId, false);
    private final AccountView sender = account(SENDER_NUMBER, "500.00");
    private final AccountView receiver = account(RECEIVER_NUMBER, "20.00");

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

    private TransactionConfirmationService service;

    @BeforeEach
    void setUp() {
        TransactionResponses responses = new TransactionResponses(Mappers.getMapper(TransactionMapper.class), accountApi);
        TransferParties parties = new TransferParties(accountApi, limitService);
        PendingTransfers pendingTransfers = new PendingTransfers(transactionRepository, catalog, accountApi,
                verificationApi, Clock.systemDefaultZone());
        service = ConfirmationServices.of(transactionRepository, catalog, responses, parties, pendingTransfers,
                limitService, accountApi, verificationApi, eventPublisher);
        lenient().when(catalog.status(any())).thenAnswer(i -> status(i.getArgument(0)));
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static AccountView account(String number, String balance) {
        return new AccountView(UUID.randomUUID(), "@AN1231", number, UUID.randomUUID(), "SAVINGS",
                AccountStatusEnum.ACTIVE.name(), new BigDecimal(balance));
    }

    private Transaction pendingWithdrawal(UUID orderedBy) {
        Transaction withdrawal = Transaction.withdrawal(UUID.randomUUID(), orderedBy, sender.id(),
                new BigDecimal("100.00"), WITHDRAWAL_REFERENCE, type(TransactionTypeEnum.WITHDRAWAL),
                status(TransactionStatusEnum.PENDING), LocalDateTime.now());
        when(transactionRepository.findByReferenceForUpdate(WITHDRAWAL_REFERENCE)).thenReturn(Optional.of(withdrawal));
        return withdrawal;
    }

    // Un depósito nace COMPLETED: se hace en un paso, sin código
    private Transaction deposit(UUID orderedBy) {
        Transaction deposit = Transaction.deposit(UUID.randomUUID(), orderedBy, receiver.id(), new BigDecimal("50.00"),
                DEPOSIT_REFERENCE, type(TransactionTypeEnum.DEPOSIT), status(TransactionStatusEnum.COMPLETED),
                LocalDateTime.now());
        when(transactionRepository.findByReferenceForUpdate(DEPOSIT_REFERENCE)).thenReturn(Optional.of(deposit));
        return deposit;
    }

    @Test
    @DisplayName("HU19 - Una referencia de retiro se confirma por la misma ruta: debita la cuenta y no mueve ninguna transferencia")
    void deberiaConfirmarUnRetiroPorLaMismaRuta() {
        // Arrange
        Transaction withdrawal = pendingWithdrawal(userId);
        when(accountApi.getAccount(sender.id())).thenReturn(sender);
        when(accountApi.applyWithdrawal(sender.id(), new BigDecimal("100.00"))).thenReturn(sender);
        when(verificationApi.verifyTransactionCode(userId, CODE)).thenReturn(CodeCheckResult.valid());

        // Act
        TransactionResponse response = service.confirm(actor, WITHDRAWAL_REFERENCE, CODE);

        // Assert
        verify(accountApi).applyWithdrawal(sender.id(), new BigDecimal("100.00"));
        verify(accountApi, never()).applyTransfer(any(), any(), any());
        verifyNoInteractions(eventPublisher);
        assertThat(withdrawal.is(TransactionStatusEnum.COMPLETED)).isTrue();
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("HU19 - Una referencia de transferencia sigue confirmándose igual: mueve el dinero y publica el comprobante")
    void deberiaConfirmarUnaTransferenciaPorLaMismaRuta() {
        // Arrange
        Transaction transaction = transfer(userId, sender.id(), receiver.id(), "120.50", TransactionStatusEnum.PENDING);
        when(transactionRepository.findByReferenceForUpdate(REFERENCE)).thenReturn(Optional.of(transaction));
        when(accountApi.getAccount(sender.id())).thenReturn(sender);
        when(accountApi.getAccount(receiver.id())).thenReturn(receiver);
        when(verificationApi.verifyTransactionCode(userId, CODE)).thenReturn(CodeCheckResult.valid());

        // Act
        TransactionResponse response = service.confirm(actor, REFERENCE, CODE);

        // Assert
        verify(accountApi).applyTransfer(sender.id(), receiver.id(), new BigDecimal("120.50"));
        verify(accountApi, never()).applyWithdrawal(any(), any());
        verify(eventPublisher).publishEvent(any(TransferCompletedEvent.class));
        assertThat(transaction.is(TransactionStatusEnum.COMPLETED)).isTrue();
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("HU19 - Los textos de error son los del tipo: un retiro dice «el retiro», una transferencia «la transferencia»")
    void deberiaUsarLosTextosDelTipo() {
        // Arrange
        pendingWithdrawal(userId);
        Transaction transaction = transfer(userId, sender.id(), receiver.id(), "120.50", TransactionStatusEnum.PENDING);
        when(transactionRepository.findByReferenceForUpdate(REFERENCE)).thenReturn(Optional.of(transaction));
        when(verificationApi.verifyTransactionCode(userId, CODE)).thenReturn(CodeCheckResult.expired());

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(actor, WITHDRAWAL_REFERENCE, CODE))
                .hasMessageContaining("el retiro fue cancelado");
        assertThatThrownBy(() -> service.confirm(actor, REFERENCE, CODE))
                .hasMessageContaining("la transferencia fue cancelada");
    }

    @Test
    @DisplayName("HU19 - Un depósito se rechaza con 400 antes de verificar el código: no devuelve su comprobante ni gasta un intento")
    void deberiaRechazarUnDeposito() {
        // Arrange
        UUID adminId = UUID.randomUUID();
        Actor admin = new Actor(adminId, true);
        deposit(adminId);

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(admin, DEPOSIT_REFERENCE, CODE))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining(DEPOSIT_REFERENCE)
                .hasMessageContaining("no se confirma con código");
        verifyNoInteractions(verificationApi, accountApi, limitService, eventPublisher);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU19 - La transacción de otro usuario sigue respondiendo como inexistente: el despacho no revela su tipo")
    void deberiaComprobarLaPropiedadAntesQueElTipo() {
        // Arrange
        pendingWithdrawal(UUID.randomUUID());
        deposit(UUID.randomUUID());

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(actor, WITHDRAWAL_REFERENCE, CODE))
                .isInstanceOf(TransactionNotFoundException.class);
        assertThatThrownBy(() -> service.confirm(actor, DEPOSIT_REFERENCE, CODE))
                .isInstanceOf(TransactionNotFoundException.class);
        verifyNoInteractions(verificationApi, accountApi);
    }
}
