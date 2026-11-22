package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.auth.api.VerificationApi;
import com.udea.digitalbank.shared.exception.accounts.AccountNotActiveException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.exception.accounts.InsufficientFundsException;
import com.udea.digitalbank.shared.exception.transactions.TransactionLimitExceededException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.udea.digitalbank.transactions.TransactionFixtures.status;
import static com.udea.digitalbank.transactions.TransactionFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link WithdrawalInitiationService}, el primer paso del retiro de HU19. Se monta como
 * TransferInitiationServiceTest: TransferParties y PendingTransfers reales sobre los mocks, y además el generador de
 * referencias real, para comprobar el prefijo RET- del comprobante.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU19 - WithdrawalInitiationService - inicio del retiro")
class WithdrawalInitiationServiceTest {

    private static final int CODE_TTL_MINUTES = 3;
    private static final String NUMBER = "100000000001";

    private final UUID userId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final Actor customer = new Actor(userId, false);

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

    private WithdrawalInitiationService service;

    @BeforeEach
    void setUp() {
        TransactionResponses responses = new TransactionResponses(Mappers.getMapper(TransactionMapper.class), accountApi);
        Clock clock = Clock.systemDefaultZone();
        TransferParties parties = new TransferParties(accountApi, limitService);
        PendingTransfers pendingTransfers = new PendingTransfers(transactionRepository, catalog, accountApi,
                verificationApi, clock);
        service = new WithdrawalInitiationService(transactionRepository, catalog, responses,
                new TransactionReferenceGenerator(), parties, pendingTransfers, limitService, accountApi,
                verificationApi, clock);
        lenient().when(catalog.status(any())).thenAnswer(i -> status(i.getArgument(0)));
        lenient().when(catalog.type(any())).thenAnswer(i -> type(i.getArgument(0)));
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(verificationApi.transactionCodeTtlMinutes()).thenReturn(CODE_TTL_MINUTES);
    }

    private AccountView account(AccountStatusEnum status, String balance) {
        return new AccountView(UUID.randomUUID(), "@AN1231", NUMBER, customerId, "SAVINGS", status.name(),
                new BigDecimal(balance));
    }

    // La cuenta propia del cliente, y sin otras transacciones pendientes que cancelar
    private AccountView ownAccount(AccountStatusEnum status, String balance) {
        AccountView account = account(status, balance);
        when(accountApi.requireOwnedAccount(userId, NUMBER)).thenReturn(account);
        return account;
    }

    private void noPendingTransactions(AccountView account) {
        when(accountApi.getAccountsOfCustomer(customerId)).thenReturn(List.of(account));
        when(transactionRepository.findPendingFromAccountsForUpdate(any(), any())).thenReturn(List.of());
    }

    private Transaction savedTransaction() {
        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("HU19 - Un cliente inicia sobre su cuenta: queda PENDING con referencia RET- y el código va al titular")
    void clienteDeberiaIniciarSobreSuCuenta() {
        // Arrange
        AccountView account = ownAccount(AccountStatusEnum.ACTIVE, "500.00");
        noPendingTransactions(account);

        // Act
        TransactionResponse response = service.initiate(customer, NUMBER, new BigDecimal("100"));

        // Assert: la transacción, de un solo lado y pendiente
        Transaction transaction = savedTransaction();
        assertThat(transaction.is(TransactionStatusEnum.PENDING)).isTrue();
        assertThat(transaction.getType().is(TransactionTypeEnum.WITHDRAWAL)).isTrue();
        assertThat(transaction.getSenderAccountId()).isEqualTo(account.id());
        assertThat(transaction.getReceiverAccountId()).isNull();
        assertThat(transaction.getUserOriginatorId()).isEqualTo(userId);
        assertThat(transaction.getAmount()).isEqualByComparingTo("100.00");
        assertThat(transaction.getReference()).startsWith("RET-");

        // Assert: el comprobante pendiente, con el vencimiento del código
        assertThat(response.getReference()).isEqualTo(transaction.getReference());
        assertThat(response.getSenderAccountNumber()).isEqualTo(NUMBER);
        assertThat(response.getReceiverAccountNumber()).isNull();
        assertThat(response.getExpiresAt()).isEqualTo(transaction.getCreatedAt().plusMinutes(CODE_TTL_MINUTES));

        // Assert: los topes de retiro del cliente, y el código al titular, que es el propio cliente
        verify(limitService).requireWithinLimits(account, TransactionTypeEnum.WITHDRAWAL, new BigDecimal("100.00"));
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(verificationApi).sendTransactionCode(eq(userId), detail.capture());
        assertThat(detail.getValue()).startsWith("Vas a retirar 100.00 de la cuenta terminada en")
                .contains(transaction.getReference()).doesNotContain("transferir").doesNotContain(NUMBER);
    }

    @Test
    @DisplayName("HU19 - Un administrador inicia sobre la cuenta de un cliente: sin topes y el código va al titular, no a él")
    void administradorDeberiaIniciarSinTopesYConElCodigoAlTitular() {
        // Arrange
        UUID adminId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        // Saldo de sobra para un monto que superaría el tope por retiro de un cliente ($2.500.000)
        AccountView account = account(AccountStatusEnum.ACTIVE, "20000000.00");
        when(accountApi.getAccountByNumber(NUMBER)).thenReturn(account);
        when(accountApi.getOwnerUserId(account.id())).thenReturn(ownerUserId);
        noPendingTransactions(account);

        // Act
        service.initiate(new Actor(adminId, true), NUMBER, new BigDecimal("9000000.00"));

        // Assert
        verifyNoInteractions(limitService);
        verify(accountApi, never()).requireOwnedAccount(any(), anyString());
        assertThat(savedTransaction().getUserOriginatorId()).isEqualTo(adminId);
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(verificationApi).sendTransactionCode(eq(ownerUserId), detail.capture());
        verify(verificationApi, never()).sendTransactionCode(eq(adminId), anyString());
        assertThat(detail.getValue()).startsWith(TransferMessages.EMPLOYEE_NOTICE + "Vas a retirar");
    }

    @Test
    @DisplayName("HU19 CA02 - Con saldo insuficiente se rechaza, no se guarda la transacción y no se envía código")
    void deberiaRechazarSinFondos() {
        // Arrange
        ownAccount(AccountStatusEnum.ACTIVE, "99.99");
        BigDecimal amount = new BigDecimal("100.00");

        // Act + Assert
        assertThatThrownBy(() -> service.initiate(customer, NUMBER, amount))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Saldo insuficiente para realizar el retiro");
        verify(transactionRepository, never()).save(any());
        verify(verificationApi, never()).sendTransactionCode(any(), anyString());
        verifyNoInteractions(limitService);
    }

    @Test
    @DisplayName("HU19 CA03 - Si un cliente supera un tope se rechaza, no se guarda la transacción y no se envía código")
    void deberiaRechazarSiSuperaUnTope() {
        // Arrange
        AccountView account = ownAccount(AccountStatusEnum.ACTIVE, "20000000.00");
        BigDecimal amount = new BigDecimal("2500000.01");
        doThrow(new TransactionLimitExceededException("El monto supera el máximo por operación de 2500000.00 COP"))
                .when(limitService).requireWithinLimits(account, TransactionTypeEnum.WITHDRAWAL, amount);

        // Act + Assert
        assertThatThrownBy(() -> service.initiate(customer, NUMBER, amount))
                .isInstanceOf(TransactionLimitExceededException.class);
        verify(transactionRepository, never()).save(any());
        verify(verificationApi, never()).sendTransactionCode(any(), anyString());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatusEnum.class, names = {"INACTIVE", "BLOCKED", "CLOSED"})
    @DisplayName("HU19 CA01 - Una cuenta que no está ACTIVE se rechaza con un mensaje de retiro, sin guardar ni enviar código")
    void deberiaRechazarUnaCuentaNoActiva(AccountStatusEnum status) {
        // Arrange
        ownAccount(status, "500.00");
        BigDecimal amount = new BigDecimal("10.00");

        // Act + Assert
        assertThatThrownBy(() -> service.initiate(customer, NUMBER, amount))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageStartingWith("No se puede retirar de la cuenta: ");
        verify(transactionRepository, never()).save(any());
        verify(verificationApi, never()).sendTransactionCode(any(), anyString());
    }

    @Test
    @DisplayName("HU19 - Para un cliente, una cuenta ajena responde igual que una inexistente y nunca se busca como administrador")
    void clienteNoDeberiaAlcanzarUnaCuentaAjena() {
        // Arrange: AccountApi responde igual a una cuenta ajena que a una inexistente
        when(accountApi.requireOwnedAccount(userId, NUMBER))
                .thenThrow(new AccountNotFoundException("Cuenta no encontrada: " + NUMBER));
        BigDecimal amount = new BigDecimal("10.00");

        // Act + Assert
        assertThatThrownBy(() -> service.initiate(customer, NUMBER, amount))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Cuenta no encontrada: " + NUMBER);
        verify(accountApi, never()).getAccountByNumber(anyString());
        verify(transactionRepository, never()).save(any());
        verify(verificationApi, never()).sendTransactionCode(any(), anyString());
    }

    @Test
    @DisplayName("HU19 - Como en la transferencia, cancela la transacción pendiente que tuviera el titular: su código deja de valer")
    void deberiaRechazarLaPendienteAnteriorDelTitular() {
        // Arrange
        AccountView account = ownAccount(AccountStatusEnum.ACTIVE, "500.00");
        Transaction previous = Transaction.withdrawal(UUID.randomUUID(), userId, account.id(), new BigDecimal("5.00"),
                "RET-20261017-7Xk2Pq9RmZ4tNvB8cHdWeA", type(TransactionTypeEnum.WITHDRAWAL),
                status(TransactionStatusEnum.PENDING), LocalDateTime.now());
        when(accountApi.getAccountsOfCustomer(customerId)).thenReturn(List.of(account));
        when(transactionRepository.findPendingFromAccountsForUpdate(eq(List.of(account.id())), any()))
                .thenReturn(List.of(previous));

        // Act
        service.initiate(customer, NUMBER, new BigDecimal("10.00"));

        // Assert
        assertThat(previous.is(TransactionStatusEnum.REJECTED)).isTrue();
    }
}
