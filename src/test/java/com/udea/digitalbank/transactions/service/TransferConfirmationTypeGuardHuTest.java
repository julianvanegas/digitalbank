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
 * Pruebas unitarias (AAA) de la guarda de tipo en {@link TransferConfirmationService#confirm}: la confirmación genérica
 * solo acepta transferencias. Existe por HU19: los retiros tendrán su propia confirmación, y una referencia de retiro
 * enviada aquí no debe gastar el código del cliente, ni cancelar el retiro, ni intentar una transferencia sin destino.
 * Se monta como TransferConfirmationServiceTest: TransferParties y PendingTransfers reales sobre los mocks.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU19 - La confirmación de transferencias rechaza las referencias que no son transferencias")
class TransferConfirmationTypeGuardHuTest {

    private static final String WITHDRAWAL_REFERENCE = "RET-20261017-7Xk2Pq9RmZ4tNvB8cHdWeA";
    private static final String DEPOSIT_REFERENCE = "DEP-20261017-7Xk2Pq9RmZ4tNvB8cHdWeA";

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

    private TransferConfirmationService service;

    @BeforeEach
    void setUp() {
        TransactionResponses responses = new TransactionResponses(Mappers.getMapper(TransactionMapper.class), accountApi);
        Clock clock = Clock.systemDefaultZone();
        TransferParties parties = new TransferParties(accountApi, limitService);
        PendingTransfers pendingTransfers = new PendingTransfers(transactionRepository, catalog, accountApi,
                verificationApi, clock);
        service = new TransferConfirmationService(transactionRepository, catalog, responses, parties,
                pendingTransfers, accountApi, verificationApi, eventPublisher);
        lenient().when(catalog.status(any())).thenAnswer(i -> status(i.getArgument(0)));
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static AccountView account(String number, String balance) {
        return new AccountView(UUID.randomUUID(), "@AN1231", number, UUID.randomUUID(), "SAVINGS",
                AccountStatusEnum.ACTIVE.name(), new BigDecimal(balance));
    }

    // Un retiro pendiente del propio usuario: lo que llegaría si el cliente equivoca el endpoint de confirmación
    private Transaction pendingWithdrawal(UUID orderedBy) {
        Transaction withdrawal = Transaction.withdrawal(UUID.randomUUID(), orderedBy, sender.id(),
                new BigDecimal("100.00"), WITHDRAWAL_REFERENCE, type(TransactionTypeEnum.WITHDRAWAL),
                status(TransactionStatusEnum.PENDING), LocalDateTime.now());
        when(transactionRepository.findByReferenceForUpdate(WITHDRAWAL_REFERENCE)).thenReturn(Optional.of(withdrawal));
        return withdrawal;
    }

    @Test
    @DisplayName("HU19 - Una referencia de retiro se rechaza con 400 y un mensaje que dice que no es una transferencia")
    void deberiaRechazarUnaReferenciaDeRetiro() {
        // Arrange
        pendingWithdrawal(userId);

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(actor, WITHDRAWAL_REFERENCE, "123456"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining(WITHDRAWAL_REFERENCE)
                .hasMessageContaining("no es de una transferencia");
    }

    @Test
    @DisplayName("HU19 - El rechazo ocurre antes de verificar el código: el cliente no gasta su código ni un intento")
    void noDeberiaConsumirElCodigo() {
        // Arrange
        pendingWithdrawal(userId);

        // Act
        assertThatThrownBy(() -> service.confirm(actor, WITHDRAWAL_REFERENCE, "123456"))
                .isInstanceOf(InvalidRequestException.class);

        // Assert
        verifyNoInteractions(verificationApi);
    }

    @Test
    @DisplayName("HU19 - No se llama a applyTransfer, no se toca ninguna cuenta y el retiro sigue PENDING sin guardarse")
    void noDeberiaMoverDineroNiCambiarElRetiro() {
        // Arrange
        Transaction withdrawal = pendingWithdrawal(userId);

        // Act
        assertThatThrownBy(() -> service.confirm(actor, WITHDRAWAL_REFERENCE, "123456"))
                .isInstanceOf(InvalidRequestException.class);

        // Assert: tampoco se cancela el retiro, que se podrá confirmar en su propio endpoint
        verifyNoInteractions(accountApi, limitService, eventPublisher);
        assertThat(withdrawal.is(TransactionStatusEnum.PENDING)).isTrue();
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU19 - Un depósito ya completado tampoco devuelve su comprobante por esta ruta")
    void deberiaRechazarUnDepositoCompletado() {
        // Arrange
        UUID adminId = UUID.randomUUID();
        Transaction deposit = Transaction.deposit(UUID.randomUUID(), adminId, receiver.id(), new BigDecimal("50.00"),
                DEPOSIT_REFERENCE, type(TransactionTypeEnum.DEPOSIT), status(TransactionStatusEnum.COMPLETED),
                LocalDateTime.now());
        when(transactionRepository.findByReferenceForUpdate(DEPOSIT_REFERENCE)).thenReturn(Optional.of(deposit));

        // Act + Assert
        Actor adminActor = new Actor(adminId, true);
        assertThatThrownBy(() -> service.confirm(adminActor, DEPOSIT_REFERENCE, "123456"))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(verificationApi, accountApi);
    }

    @Test
    @DisplayName("HU19 - El retiro de otro usuario sigue respondiendo como inexistente: la guarda no revela su tipo")
    void deberiaComprobarLaPropiedadAntesQueElTipo() {
        // Arrange
        pendingWithdrawal(UUID.randomUUID());

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(actor, WITHDRAWAL_REFERENCE, "123456"))
                .isInstanceOf(TransactionNotFoundException.class);
        verifyNoInteractions(verificationApi, accountApi);
    }

    @Test
    @DisplayName("HU19 - Una referencia de transferencia sigue confirmándose igual que antes")
    void deberiaSeguirConfirmandoTransferencias() {
        // Arrange
        Transaction transaction = transfer(userId, sender.id(), receiver.id(), "120.50", TransactionStatusEnum.PENDING);
        when(transactionRepository.findByReferenceForUpdate(REFERENCE)).thenReturn(Optional.of(transaction));
        when(accountApi.getAccount(sender.id())).thenReturn(sender);
        when(accountApi.getAccount(receiver.id())).thenReturn(receiver);
        when(verificationApi.verifyTransactionCode(userId, "123456")).thenReturn(CodeCheckResult.valid());

        // Act
        TransactionResponse response = service.confirm(actor, REFERENCE, "123456");

        // Assert
        verify(accountApi).applyTransfer(sender.id(), receiver.id(), new BigDecimal("120.50"));
        assertThat(transaction.is(TransactionStatusEnum.COMPLETED)).isTrue();
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
    }
}
