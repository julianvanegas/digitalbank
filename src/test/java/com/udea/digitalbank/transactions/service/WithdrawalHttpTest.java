package com.udea.digitalbank.transactions.service;

import org.springframework.context.ApplicationEventPublisher;
import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.auth.api.CodeCheckResult;
import com.udea.digitalbank.auth.api.VerificationApi;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.exception.transactions.TransactionLimitExceededException;
import com.udea.digitalbank.support.HttpTestSupport;
import com.udea.digitalbank.transactions.controller.TransactionController;
import com.udea.digitalbank.transactions.controller.WithdrawalController;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.mapper.TransactionMapper;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.udea.digitalbank.support.HttpTestSupport.authenticate;
import static com.udea.digitalbank.support.HttpTestSupport.body;
import static com.udea.digitalbank.support.HttpTestSupport.clearAuthentication;
import static com.udea.digitalbank.support.HttpTestSupport.get;
import static com.udea.digitalbank.support.HttpTestSupport.perform;
import static com.udea.digitalbank.support.HttpTestSupport.post;
import static com.udea.digitalbank.transactions.TransactionFixtures.status;
import static com.udea.digitalbank.transactions.TransactionFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contrato HTTP del retiro (HU19): POST /withdrawals y POST /{referencia}/confirm con la referencia de un retiro, y el comprobante por
 * GET /{referencia}. Vive en transactions.service y no en transactions.controller porque usa el controller con los
 * servicios REALES detrás, y TransferParties, PendingTransfers, TransactionAccess y el generador de
 * referencias son de paquete. Con los servicios simulados, la referencia, los mensajes y el "no se guarda nada" los
 * escribiría la propia prueba. Solo se simulan el repositorio, el catálogo, los topes, accounts y auth.
 * <p>
 * Los topes van simulados: aquí se prueba que su rechazo llega como 409; la regla la prueban HU14 y el paso E.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU19 - Contrato HTTP del retiro")
class WithdrawalHttpTest {

    private static final String BASE = "/api/v1/transactions";
    private static final String NUMBER = "100000000001";
    private static final String CODE = "123456";

    private final UUID userId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final AccountView account = new AccountView(UUID.randomUUID(), "@AN1231", NUMBER, customerId, "SAVINGS",
            AccountStatusEnum.ACTIVE.name(), new BigDecimal("500.00"));

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

    private MockMvc mockMvc;
    private TransactionResponses responses;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.systemDefaultZone();
        responses = new TransactionResponses(Mappers.getMapper(TransactionMapper.class), accountApi);
        TransferParties parties = new TransferParties(accountApi, limitService);
        PendingTransfers pendingTransfers = new PendingTransfers(transactionRepository, catalog, accountApi,
                verificationApi, clock);
        WithdrawalInitiationService initiation = new WithdrawalInitiationService(transactionRepository, catalog,
                responses, new TransactionReferenceGenerator(), parties, pendingTransfers, limitService, accountApi,
                verificationApi, clock);
        TransactionConfirmationService confirmation = ConfirmationServices.of(transactionRepository, catalog,
                responses, parties, pendingTransfers, limitService, accountApi, verificationApi,
                mock(ApplicationEventPublisher.class));
        // La confirmación del retiro es la de las transferencias: POST /{referencia}/confirm, en TransactionController
        mockMvc = HttpTestSupport.mockMvc(new WithdrawalController(initiation),
                new TransactionController(mock(TransferInitiationService.class), confirmation,
                        mock(TransactionQueryService.class)));

        lenient().when(catalog.status(any())).thenAnswer(i -> status(i.getArgument(0)));
        lenient().when(catalog.type(any())).thenAnswer(i -> type(i.getArgument(0)));
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(verificationApi.transactionCodeTtlMinutes()).thenReturn(3);
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    private static String withdrawalBody(String amount) {
        return "{\"accountNumber\":\"" + NUMBER + "\",\"amount\":" + amount + "}";
    }

    // El cliente sobre su propia cuenta, sin otras transacciones pendientes que cancelar
    private void ownAccountWithoutPending() {
        when(accountApi.requireOwnedAccount(userId, NUMBER)).thenReturn(account);
        when(accountApi.getAccountsOfCustomer(customerId)).thenReturn(List.of(account));
        when(transactionRepository.findPendingFromAccountsForUpdate(any(), any())).thenReturn(List.of());
    }

    private Transaction pendingWithdrawal(UUID orderedBy, String reference) {
        Transaction withdrawal = Transaction.withdrawal(UUID.randomUUID(), orderedBy, account.id(),
                new BigDecimal("100.00"), reference, type(TransactionTypeEnum.WITHDRAWAL),
                status(TransactionStatusEnum.PENDING), LocalDateTime.now());
        when(transactionRepository.findByReferenceForUpdate(reference)).thenReturn(Optional.of(withdrawal));
        return withdrawal;
    }

    @Nested
    @DisplayName("HU19 - POST /transactions/withdrawals")
    class Initiate {

        @Test
        @DisplayName("HU19 - Un cliente inicia un retiro: 201 con la transacción PENDING y una referencia RET- generada")
        void deberiaResponder201ConElRetiroPendiente() throws Exception {
            // Arrange
            authenticate(userId, false);
            ownAccountWithoutPending();

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE + "/withdrawals", withdrawalBody("100.00")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(201);
            assertThat(body(response)).contains("\"status\":\"PENDING\"", "\"type\":\"WITHDRAWAL\"",
                    "\"reference\":\"RET-", "\"senderAccountNumber\":\"" + NUMBER + "\"", "\"expiresAt\"");
            verify(transactionRepository).save(any(Transaction.class));
            verify(verificationApi).sendTransactionCode(eq(userId), anyString());
        }

        @Test
        @DisplayName("HU19 CA02 - Saldo insuficiente: 409 con 'Saldo insuficiente', sin crear transacción ni enviar código")
        void deberiaResponder409SinFondos() throws Exception {
            // Arrange
            authenticate(userId, false);
            when(accountApi.requireOwnedAccount(userId, NUMBER)).thenReturn(account);

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE + "/withdrawals", withdrawalBody("500.01")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(body(response)).contains("Saldo insuficiente para realizar el retiro");
            verify(transactionRepository, never()).save(any());
            verify(verificationApi, never()).sendTransactionCode(any(), anyString());
        }

        @Test
        @DisplayName("HU19 CA03 - Un cliente que supera un tope: 409 con el mensaje del tope, sin crear transacción")
        void deberiaResponder409SiSuperaUnTope() throws Exception {
            // Arrange
            authenticate(userId, false);
            when(accountApi.requireOwnedAccount(userId, NUMBER)).thenReturn(account);
            doThrow(new TransactionLimitExceededException("Alcanzaste el máximo de 4 operaciones diarias para esta cuenta"))
                    .when(limitService).requireWithinLimits(any(), eq(TransactionTypeEnum.WITHDRAWAL), any());

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE + "/withdrawals", withdrawalBody("100.00")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(body(response)).contains("Alcanzaste el máximo de 4 operaciones diarias");
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("HU19 - Para un cliente, una cuenta ajena: 404 como una inexistente, sin buscarla como administrador")
        void deberiaResponder404ConUnaCuentaAjena() throws Exception {
            // Arrange
            authenticate(userId, false);
            when(accountApi.requireOwnedAccount(userId, NUMBER))
                    .thenThrow(new AccountNotFoundException("Cuenta no encontrada: " + NUMBER));

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE + "/withdrawals", withdrawalBody("100.00")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(body(response)).contains("Cuenta no encontrada: " + NUMBER);
            verify(accountApi, never()).getAccountByNumber(anyString());
            verify(transactionRepository, never()).save(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "0.00", "-5.00", "10.001", "null"})
        @DisplayName("HU19 - Monto inválido (cero, negativo, con más de dos decimales o ausente): 400 sin tocar nada")
        void deberiaResponder400ConUnMontoInvalido(String amount) throws Exception {
            // Arrange
            authenticate(userId, false);

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE + "/withdrawals", withdrawalBody(amount)));

            // Assert: la validación corta antes del servicio, así que el setScale(2) nunca ve más de dos decimales
            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(accountApi, transactionRepository, verificationApi);
        }

        // @Digits cuenta también los ceros decimales: 100.000 tiene escala 3 y se rechaza. Así el setScale(2) del
        // servicio nunca recibe una escala mayor que 2 y no puede lanzar ArithmeticException (un 500)
        @Test
        @DisplayName("HU19 - Un monto con ceros decimales de más (100.000) también se rechaza con 400, nunca llega al servicio")
        void deberiaRechazarCerosDecimalesDeMas() throws Exception {
            // Arrange
            authenticate(userId, false);

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE + "/withdrawals", withdrawalBody("100.000")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(body(response)).contains("El monto admite como máximo 2 decimales");
            verifyNoInteractions(accountApi, transactionRepository);
        }

        @Test
        @DisplayName("HU19 - Un monto con un decimal (100.5) se guarda con escala 2 (100.50), sin redondear")
        void deberiaFijarLaEscalaEnDos() throws Exception {
            // Arrange
            authenticate(userId, false);
            ownAccountWithoutPending();

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE + "/withdrawals", withdrawalBody("100.5")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(201);
            ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(saved.capture());
            assertThat(saved.getValue().getAmount()).isEqualByComparingTo("100.50");
            assertThat(saved.getValue().getAmount().scale()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("HU19 - POST /transactions/{referencia}/confirm con la referencia de un retiro")
    class Confirm {

        private static final String REFERENCE = "RET-20261017-7Xk2Pq9RmZ4tNvB8cHdWeA";

        @Test
        @DisplayName("HU19 CA01 - Con el código correcto: 200 con el retiro COMPLETED y la cuenta debitada")
        void deberiaResponder200ConElRetiroCompletado() throws Exception {
            // Arrange
            authenticate(userId, false);
            pendingWithdrawal(userId, REFERENCE);
            when(verificationApi.verifyTransactionCode(userId, CODE)).thenReturn(CodeCheckResult.valid());
            when(accountApi.getAccount(account.id())).thenReturn(account);
            when(accountApi.applyWithdrawal(account.id(), new BigDecimal("100.00"))).thenReturn(account);

            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    post(BASE + "/" + REFERENCE + "/confirm", "{\"code\":\"" + CODE + "\"}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body(response)).contains("\"status\":\"COMPLETED\"", "\"reference\":\"" + REFERENCE + "\"");
            verify(accountApi).applyWithdrawal(account.id(), new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("HU19 CA04 - Con un código equivocado: 400 con los intentos restantes, el retiro sigue PENDING y no se debita")
        void deberiaResponder400ConUnCodigoEquivocado() throws Exception {
            // Arrange
            authenticate(userId, false);
            Transaction withdrawal = pendingWithdrawal(userId, REFERENCE);
            when(verificationApi.verifyTransactionCode(userId, CODE)).thenReturn(CodeCheckResult.wrong(2));

            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    post(BASE + "/" + REFERENCE + "/confirm", "{\"code\":\"" + CODE + "\"}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(body(response)).contains("Código incorrecto. Te quedan 2 intentos");
            assertThat(withdrawal.is(TransactionStatusEnum.PENDING)).isTrue();
            verify(accountApi, never()).applyWithdrawal(any(), any());
        }

        @Test
        @DisplayName("HU19 - Un código con formato inválido: 400 de validación, sin gastar un intento")
        void deberiaResponder400ConUnCodigoMalFormado() throws Exception {
            // Arrange
            authenticate(userId, false);

            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    post(BASE + "/" + REFERENCE + "/confirm", "{\"code\":\"12ab\"}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(verificationApi, transactionRepository);
        }
    }

    /*
     * Lo que pidió verificar el PR: que GET /{referencia} devuelve el comprobante de un retiro al titular y al
     * administrador que lo inició. Usa el TransactionController real con el TransactionQueryService y el
     * TransactionAccess reales; los servicios de transferencia no intervienen y van simulados.
     */
    @Nested
    @DisplayName("HU19 CA01 - GET /transactions/{referencia} - comprobante de un retiro")
    class Receipt {

        private static final String REFERENCE = "RET-20261017-9Ab2Cd3Ef4Gh5Jk6Mn7Pq8";

        private final UUID adminId = UUID.randomUUID();
        private final UUID ownerUserId = UUID.randomUUID();
        private MockMvc receiptMvc;

        @BeforeEach
        void setUpReceipt() {
            TransactionQueryService queryService = new TransactionQueryService(transactionRepository, responses,
                    new TransactionAccess(accountApi));
            receiptMvc = HttpTestSupport.mockMvc(new TransactionController(mock(TransferInitiationService.class),
                    mock(TransactionConfirmationService.class), queryService));

            // Un retiro que inició un administrador sobre la cuenta del titular y ya se completó
            Transaction withdrawal = Transaction.withdrawal(UUID.randomUUID(), adminId, account.id(),
                    new BigDecimal("100.00"), REFERENCE, type(TransactionTypeEnum.WITHDRAWAL),
                    status(TransactionStatusEnum.PENDING), LocalDateTime.now());
            withdrawal.complete(status(TransactionStatusEnum.COMPLETED));
            when(transactionRepository.findByReference(REFERENCE)).thenReturn(Optional.of(withdrawal));
        }

        private void numberIsKnown() {
            when(accountApi.getAccount(account.id())).thenReturn(account);
        }

        @Test
        @DisplayName("HU19 CA01 - El titular ve el comprobante del retiro que un administrador hizo en su cuenta")
        void elTitularDeberiaVerElComprobante() throws Exception {
            // Arrange: no lo ordenó, pero es el titular de la cuenta de origen
            authenticate(ownerUserId, false);
            when(accountApi.getOwnerUserId(account.id())).thenReturn(ownerUserId);
            numberIsKnown();

            // Act
            MockHttpServletResponse response = perform(receiptMvc, get(BASE + "/" + REFERENCE));

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body(response)).contains("\"reference\":\"" + REFERENCE + "\"", "\"type\":\"WITHDRAWAL\"",
                    "\"status\":\"COMPLETED\"", "\"senderAccountNumber\":\"" + NUMBER + "\"");
        }

        @Test
        @DisplayName("HU19 CA01 - El administrador que inició el retiro ve su comprobante")
        void elAdministradorQueLoInicioDeberiaVerElComprobante() throws Exception {
            // Arrange
            authenticate(adminId, true);
            numberIsKnown();

            // Act
            MockHttpServletResponse response = perform(receiptMvc, get(BASE + "/" + REFERENCE));

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body(response)).contains("\"reference\":\"" + REFERENCE + "\"", "\"type\":\"WITHDRAWAL\"",
                    "\"status\":\"COMPLETED\"");
        }

        @Test
        @DisplayName("HU19 - Otro cliente, que ni lo ordenó ni es el titular, recibe 404")
        void otroClienteNoDeberiaVerElComprobante() throws Exception {
            // Arrange
            UUID stranger = UUID.randomUUID();
            authenticate(stranger, false);
            when(accountApi.getOwnerUserId(account.id())).thenReturn(ownerUserId);

            // Act
            MockHttpServletResponse response = perform(receiptMvc, get(BASE + "/" + REFERENCE));

            // Assert
            assertThat(response.getStatus()).isEqualTo(404);
        }
    }
}
