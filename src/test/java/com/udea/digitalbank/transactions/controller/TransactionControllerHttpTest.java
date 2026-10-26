package com.udea.digitalbank.transactions.controller;

import com.udea.digitalbank.shared.exception.TooManyRequestsException;
import com.udea.digitalbank.shared.exception.accounts.InsufficientFundsException;
import com.udea.digitalbank.shared.exception.transactions.InvalidReceiverAccountException;
import com.udea.digitalbank.shared.exception.transactions.InvalidTransactionCodeException;
import com.udea.digitalbank.shared.exception.transactions.SameAccountTransferException;
import com.udea.digitalbank.shared.exception.transactions.TransactionCodeExpiredException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.support.HttpTestSupport;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.dto.TransferRequest;
import com.udea.digitalbank.transactions.service.TransactionQueryService;
import com.udea.digitalbank.transactions.service.TransferService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static com.udea.digitalbank.support.HttpTestSupport.authenticate;
import static com.udea.digitalbank.support.HttpTestSupport.body;
import static com.udea.digitalbank.support.HttpTestSupport.clearAuthentication;
import static com.udea.digitalbank.support.HttpTestSupport.perform;
import static com.udea.digitalbank.support.HttpTestSupport.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas de contrato HTTP (AAA) de {@link TransactionController}: qué código y qué mensaje recibe el cliente de la
 * API al iniciar una transferencia (HU20) y al confirmarla con el código 2FA (HU21). Los códigos son los que fija el
 * ADR-0004 del backend.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU20 / HU21 - Contrato HTTP de transferencias")
class TransactionControllerHttpTest {

    private static final String TRANSFERS = "/api/v1/transactions/transfers";
    private static final String REFERENCE = "TRF-20261026-7Xk2Pq9RmZ4tNvB8cHdWeA";
    private static final String CONFIRM = "/api/v1/transactions/" + REFERENCE + "/confirm";
    private static final String SENDER = "100000000001";
    private static final String RECEIVER = "200000000002";

    @Mock
    private TransferService transferService;
    @Mock
    private TransactionQueryService queryService;

    private MockMvc mockMvc;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = HttpTestSupport.mockMvc(new TransactionController(transferService, queryService));
        authenticate(userId, false);
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    private static String transferBody(String sender, String receiver, String amount) {
        return "{\"senderAccountNumber\":\"" + sender + "\",\"receiverAccountNumber\":\"" + receiver
                + "\",\"amount\":" + amount + "}";
    }

    private static TransactionResponse transaction(String status) {
        TransactionResponse response = new TransactionResponse();
        response.setReference(REFERENCE);
        response.setType("TRANSFER");
        response.setStatus(status);
        response.setAmount(new BigDecimal("100000.00"));
        response.setSenderAccountNumber(SENDER);
        response.setReceiverAccountNumber(RECEIVER);
        return response;
    }

    private void initiateThrows(RuntimeException error) {
        when(transferService.initiate(eq(new Actor(userId, false)), any(TransferRequest.class))).thenThrow(error);
    }

    private void confirmThrows(String code, RuntimeException error) {
        when(transferService.confirm(new Actor(userId, false), REFERENCE, code)).thenThrow(error);
    }

    @Nested
    @DisplayName("HU20 - POST /transactions/transfers")
    class Initiate {

        @Test
        @DisplayName("HU20 CA01 - Transferencia válida: 201 en estado PENDING con su referencia")
        void deberiaDejarLaTransferenciaPendiente() throws Exception {
            // Arrange
            when(transferService.initiate(eq(new Actor(userId, false)), any(TransferRequest.class)))
                    .thenReturn(transaction("PENDING"));

            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    post(TRANSFERS, transferBody(SENDER, RECEIVER, "100000.00")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(201);
            assertThat(body(response)).contains("\"status\":\"PENDING\"").contains(REFERENCE);
        }

        @Test
        @DisplayName("HU20 CA02 - Saldo insuficiente: 409 que informa la falta de fondos")
        void deberiaInformarFondosInsuficientes() throws Exception {
            // Arrange
            initiateThrows(new InsufficientFundsException("Fondos insuficientes en la cuenta de origen"));

            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    post(TRANSFERS, transferBody(SENDER, RECEIVER, "500001.00")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(body(response)).contains("Fondos insuficientes");
        }

        @Test
        @DisplayName("HU20 CA03 - Misma cuenta de origen y destino: 400 que indica que no se puede")
        void deberiaRechazarLaMismaCuenta() throws Exception {
            // Arrange
            initiateThrows(new SameAccountTransferException("No se pueden realizar transferencias a la misma cuenta"));

            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    post(TRANSFERS, transferBody(SENDER, SENDER, "10000.00")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(body(response)).contains("misma cuenta");
        }

        @Test
        @DisplayName("HU20 CA04 - Cuenta de destino inexistente o bloqueada: 400 que informa su condición")
        void deberiaInformarLaCondicionDelDestino() throws Exception {
            // Arrange
            initiateThrows(new InvalidReceiverAccountException(
                    "La cuenta de destino no puede recibir transferencias: está bloqueada"));

            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    post(TRANSFERS, transferBody(SENDER, RECEIVER, "10000.00")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(body(response)).contains("está bloqueada");
        }

        @ParameterizedTest(name = "monto {0}")
        @ValueSource(strings = {"0", "-10.00", "10.001"})
        @DisplayName("HU20 complementario - Monto cero, negativo o con más de 2 decimales: 400 y no se inicia")
        void deberiaRechazarUnMontoInvalido(String amount) throws Exception {
            // Act
            MockHttpServletResponse response = perform(mockMvc, post(TRANSFERS, transferBody(SENDER, RECEIVER, amount)));

            // Assert
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(body(response)).contains("amount");
            verifyNoInteractions(transferService);
        }

        @Test
        @DisplayName("HU20 CA04 - Un número de cuenta que no tiene 12 dígitos: 400 y no se inicia")
        void deberiaRechazarUnNumeroDeCuentaMalFormado() throws Exception {
            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    post(TRANSFERS, transferBody(SENDER, "12345", "10000.00")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(body(response)).contains("receiverAccountNumber");
            verifyNoInteractions(transferService);
        }
    }

    @Nested
    @DisplayName("HU21 - POST /transactions/{referencia}/confirm")
    class Confirm {

        @Test
        @DisplayName("HU21 CA01 - Código correcto: 200 con la transferencia COMPLETED")
        void deberiaCompletarLaTransferencia() throws Exception {
            // Arrange
            when(transferService.confirm(new Actor(userId, false), REFERENCE, "123456"))
                    .thenReturn(transaction("COMPLETED"));

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(CONFIRM, "{\"code\":\"123456\"}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body(response)).contains("\"status\":\"COMPLETED\"");
        }

        @Test
        @DisplayName("HU21 CA02 - Código incorrecto en el primer o segundo intento: 400 con los intentos que quedan")
        void deberiaInformarLosIntentosQueQuedan() throws Exception {
            // Arrange
            confirmThrows("000000", new InvalidTransactionCodeException("Código incorrecto. Te quedan 2 intentos"));

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(CONFIRM, "{\"code\":\"000000\"}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(body(response)).contains("Te quedan 2 intentos");
        }

        @Test
        @DisplayName("HU21 CA03 - Tercer código incorrecto: 429 que informa que la transferencia fue cancelada")
        void deberiaCancelarAlTercerFallo() throws Exception {
            // Arrange
            confirmThrows("000000", new TooManyRequestsException(
                    "Superaste el número de intentos: la transferencia fue cancelada. Inicia una nueva", 0));

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(CONFIRM, "{\"code\":\"000000\"}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(429);
            assertThat(response.getHeader("Retry-After")).isNull();
            assertThat(body(response)).contains("fue cancelada");
        }

        @Test
        @DisplayName("HU21 CA04 - Código vencido: 409 que indica que expiró y que se debe reiniciar")
        void deberiaInformarQueElCodigoExpiro() throws Exception {
            // Arrange
            confirmThrows("123456", new TransactionCodeExpiredException(
                    "El código expiró: la transferencia fue cancelada. Inicia el proceso de nuevo"));

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(CONFIRM, "{\"code\":\"123456\"}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(body(response)).contains("expiró").contains("Inicia el proceso de nuevo");
        }

        @ParameterizedTest(name = "código \"{0}\"")
        @ValueSource(strings = {"", "12345", "12a456"})
        @DisplayName("HU21 CA02 - Un código que no son 6 dígitos: 400 y no gasta intentos")
        void deberiaRechazarUnCodigoMalFormado(String code) throws Exception {
            // Act
            MockHttpServletResponse response = perform(mockMvc, post(CONFIRM, "{\"code\":\"" + code + "\"}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(transferService);
        }
    }
}
