package com.udea.digitalbank.transactions.service;

import org.springframework.context.ApplicationEventPublisher;
import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.auth.api.CodeCheckResult;
import com.udea.digitalbank.auth.api.VerificationApi;
import com.udea.digitalbank.shared.exception.TooManyRequestsException;
import com.udea.digitalbank.shared.exception.accounts.InsufficientFundsException;
import com.udea.digitalbank.shared.exception.transactions.InvalidTransactionCodeException;
import com.udea.digitalbank.shared.exception.transactions.TransactionCodeExpiredException;
import com.udea.digitalbank.shared.exception.transactions.TransactionLimitExceededException;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static com.udea.digitalbank.transactions.TransactionFixtures.status;
import static com.udea.digitalbank.transactions.TransactionFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link TransactionConfirmationService} con un retiro, el segundo paso del retiro de HU19. Se monta como
 * TransferConfirmationServiceTest: TransferParties y PendingTransfers reales sobre los mocks. Que el rechazo se
 * confirme en la base gracias al noRollbackFor no se puede ver con mocks: lo prueba
 * WithdrawalConfirmationTransactionTest con el proxy transaccional de Spring.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU19 - TransactionConfirmationService (retiros) - confirmación del retiro")
class WithdrawalConfirmationServiceTest {

    private static final String WITHDRAWAL_REFERENCE = "RET-20261017-7Xk2Pq9RmZ4tNvB8cHdWeA";
    private static final String NUMBER = "100000000001";
    private static final String CODE = "123456";
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    private final UUID userId = UUID.randomUUID();
    private final Actor customer = new Actor(userId, false);
    private final AccountView account = account("500.00");

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

    private TransactionConfirmationService service;

    @BeforeEach
    void setUp() {
        TransactionResponses responses = new TransactionResponses(Mappers.getMapper(TransactionMapper.class), accountApi);
        TransferParties parties = new TransferParties(accountApi, limitService);
        PendingTransfers pendingTransfers = new PendingTransfers(transactionRepository, catalog, accountApi,
                verificationApi, Clock.systemDefaultZone());
        service = ConfirmationServices.of(transactionRepository, catalog, responses, parties, pendingTransfers,
                limitService, accountApi, verificationApi, mock(ApplicationEventPublisher.class));
        lenient().when(catalog.status(any())).thenAnswer(i -> status(i.getArgument(0)));
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static AccountView account(String balance) {
        return new AccountView(UUID.randomUUID(), "@AN1231", NUMBER, UUID.randomUUID(), "SAVINGS",
                AccountStatusEnum.ACTIVE.name(), new BigDecimal(balance));
    }

    // Un retiro PENDING ordenado por orderedBy, que se encuentra por su referencia con la fila bloqueada
    private Transaction pendingWithdrawal(UUID orderedBy) {
        Transaction withdrawal = Transaction.withdrawal(UUID.randomUUID(), orderedBy, account.id(), AMOUNT,
                WITHDRAWAL_REFERENCE, type(TransactionTypeEnum.WITHDRAWAL), status(TransactionStatusEnum.PENDING),
                LocalDateTime.now());
        when(transactionRepository.findByReferenceForUpdate(WITHDRAWAL_REFERENCE)).thenReturn(Optional.of(withdrawal));
        return withdrawal;
    }

    private void codeIs(UUID owner, CodeCheckResult result) {
        when(verificationApi.verifyTransactionCode(owner, CODE)).thenReturn(result);
    }

    // El débito que hace accounts con la fila bloqueada: devuelve la cuenta con el saldo ya actualizado
    private void debitSucceeds() {
        when(accountApi.applyWithdrawal(account.id(), AMOUNT)).thenReturn(account("400.00"));
    }

    @Test
    @DisplayName("HU19 CA01 - Con el código correcto revalida los topes, debita la cuenta y el retiro queda COMPLETED")
    void deberiaDebitarYCompletarConElCodigoCorrecto() {
        // Arrange
        Transaction withdrawal = pendingWithdrawal(userId);
        codeIs(userId, CodeCheckResult.valid());
        when(accountApi.getAccount(account.id())).thenReturn(account);
        debitSucceeds();

        // Act
        TransactionResponse response = service.confirm(customer, WITHDRAWAL_REFERENCE, CODE);

        // Assert
        verify(limitService).requireWithinLimits(account, TransactionTypeEnum.WITHDRAWAL, AMOUNT);
        verify(accountApi).applyWithdrawal(account.id(), AMOUNT);
        assertThat(withdrawal.is(TransactionStatusEnum.COMPLETED)).isTrue();
        verify(transactionRepository).save(withdrawal);
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getReference()).isEqualTo(WITHDRAWAL_REFERENCE);
        assertThat(response.getSenderAccountNumber()).isEqualTo(NUMBER);
        assertThat(response.getReceiverAccountNumber()).isNull();
    }

    @Test
    @DisplayName("HU19 CA04 - Código equivocado: se rechaza con los intentos que quedan, el retiro sigue PENDING y no se debita")
    void deberiaRechazarUnCodigoEquivocadoSinDebitar() {
        // Arrange
        Transaction withdrawal = pendingWithdrawal(userId);
        codeIs(userId, CodeCheckResult.wrong(2));

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(customer, WITHDRAWAL_REFERENCE, CODE))
                .isInstanceOf(InvalidTransactionCodeException.class)
                .hasMessage("Código incorrecto. Te quedan 2 intentos");
        // El intento lo registra auth al verificar; que sobreviva a la excepción lo prueba la clase transaccional
        verify(verificationApi).verifyTransactionCode(userId, CODE);
        assertThat(withdrawal.is(TransactionStatusEnum.PENDING)).isTrue();
        verify(accountApi, never()).applyWithdrawal(any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU19 CA04 - Intentos agotados: el retiro queda REJECTED, se guarda y no se debita")
    void deberiaCancelarAlAgotarLosIntentos() {
        // Arrange
        Transaction withdrawal = pendingWithdrawal(userId);
        codeIs(userId, CodeCheckResult.exhausted());

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(customer, WITHDRAWAL_REFERENCE, CODE))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("el retiro fue cancelado");
        assertThat(withdrawal.is(TransactionStatusEnum.REJECTED)).isTrue();
        verify(transactionRepository).save(withdrawal);
        verify(accountApi, never()).applyWithdrawal(any(), any());
    }

    @Test
    @DisplayName("HU19 - Código vencido: el retiro queda REJECTED y no se debita")
    void deberiaCancelarConElCodigoVencido() {
        // Arrange
        Transaction withdrawal = pendingWithdrawal(userId);
        codeIs(userId, CodeCheckResult.expired());

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(customer, WITHDRAWAL_REFERENCE, CODE))
                .isInstanceOf(TransactionCodeExpiredException.class)
                .hasMessageContaining("el retiro fue cancelado");
        assertThat(withdrawal.is(TransactionStatusEnum.REJECTED)).isTrue();
        verify(transactionRepository).save(withdrawal);
        verify(accountApi, never()).applyWithdrawal(any(), any());
    }

    @Test
    @DisplayName("HU19 CA02 - Si al confirmar el saldo ya no alcanza, el retiro queda REJECTED, se guarda y se relanza el error")
    void deberiaCancelarSiElSaldoYaNoAlcanza() {
        // Arrange
        Transaction withdrawal = pendingWithdrawal(userId);
        codeIs(userId, CodeCheckResult.valid());
        when(accountApi.getAccount(account.id())).thenReturn(account);
        InsufficientFundsException noFunds = new InsufficientFundsException("Fondos insuficientes en la cuenta");
        when(accountApi.applyWithdrawal(account.id(), AMOUNT)).thenThrow(noFunds);

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(customer, WITHDRAWAL_REFERENCE, CODE)).isSameAs(noFunds);
        assertThat(withdrawal.is(TransactionStatusEnum.REJECTED)).isTrue();
        verify(transactionRepository).save(withdrawal);
    }

    @Test
    @DisplayName("HU19 CA03 - Si al confirmar un cliente ya superaría un tope, el retiro queda REJECTED y no se debita")
    void deberiaCancelarSiYaSuperaUnTope() {
        // Arrange
        Transaction withdrawal = pendingWithdrawal(userId);
        codeIs(userId, CodeCheckResult.valid());
        when(accountApi.getAccount(account.id())).thenReturn(account);
        doThrow(new TransactionLimitExceededException("Alcanzaste el máximo de 4 operaciones diarias para esta cuenta"))
                .when(limitService).requireWithinLimits(account, TransactionTypeEnum.WITHDRAWAL, AMOUNT);

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(customer, WITHDRAWAL_REFERENCE, CODE))
                .isInstanceOf(TransactionLimitExceededException.class);
        assertThat(withdrawal.is(TransactionStatusEnum.REJECTED)).isTrue();
        verify(transactionRepository).save(withdrawal);
        verify(accountApi, never()).applyWithdrawal(any(), any());
    }

    @Test
    @DisplayName("HU19 CA01 - Es idempotente: confirmar dos veces devuelve el mismo comprobante y debita una sola vez")
    void noDeberiaDebitarDosVeces() {
        // Arrange
        pendingWithdrawal(userId);
        codeIs(userId, CodeCheckResult.valid());
        when(accountApi.getAccount(account.id())).thenReturn(account);
        debitSucceeds();

        // Act
        TransactionResponse first = service.confirm(customer, WITHDRAWAL_REFERENCE, CODE);
        TransactionResponse second = service.confirm(customer, WITHDRAWAL_REFERENCE, CODE);

        // Assert
        assertThat(second.getReference()).isEqualTo(first.getReference());
        assertThat(second.getStatus()).isEqualTo("COMPLETED");
        verify(accountApi, times(1)).applyWithdrawal(any(), any());
        verify(verificationApi, times(1)).verifyTransactionCode(any(), any());
    }

    @Test
    @DisplayName("HU19 - Un retiro que inició un administrador se confirma con el código del titular, verificado contra su userId, y sin topes")
    void administradorDeberiaConfirmarConElCodigoDelTitular() {
        // Arrange
        UUID adminId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        Transaction withdrawal = pendingWithdrawal(adminId);
        when(accountApi.getOwnerUserId(account.id())).thenReturn(ownerUserId);
        codeIs(ownerUserId, CodeCheckResult.valid());
        debitSucceeds();

        // Act
        service.confirm(new Actor(adminId, true), WITHDRAWAL_REFERENCE, CODE);

        // Assert
        verify(verificationApi).verifyTransactionCode(ownerUserId, CODE);
        verify(verificationApi, never()).verifyTransactionCode(adminId, CODE);
        verifyNoInteractions(limitService);
        assertThat(withdrawal.is(TransactionStatusEnum.COMPLETED)).isTrue();
    }

    @Test
    @DisplayName("HU19 - Un retiro que inició un administrador no lo pueden confirmar ni el titular ni otro administrador: 404")
    void soloElAdministradorQueLoInicioPuedeConfirmarlo() {
        // Arrange
        UUID adminId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        Transaction withdrawal = pendingWithdrawal(adminId);
        Actor owner = new Actor(ownerUserId, false);
        Actor otherAdmin = new Actor(UUID.randomUUID(), true);

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(owner, WITHDRAWAL_REFERENCE, CODE))
                .isInstanceOf(TransactionNotFoundException.class);
        assertThatThrownBy(() -> service.confirm(otherAdmin, WITHDRAWAL_REFERENCE, CODE))
                .isInstanceOf(TransactionNotFoundException.class);
        verifyNoInteractions(verificationApi, accountApi);
        assertThat(withdrawal.is(TransactionStatusEnum.PENDING)).isTrue();
    }
}
