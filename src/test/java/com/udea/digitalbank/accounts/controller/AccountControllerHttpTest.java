package com.udea.digitalbank.accounts.controller;

import com.udea.digitalbank.accounts.api.AccountTypeEnum;
import com.udea.digitalbank.accounts.dto.AccountBalanceResponse;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.service.AccountLifecycleService;
import com.udea.digitalbank.accounts.service.AccountOpeningService;
import com.udea.digitalbank.accounts.service.AccountQueryService;
import com.udea.digitalbank.shared.exception.accounts.AccountLimitReachedException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotBlockableException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotUnblockableException;
import com.udea.digitalbank.shared.exception.accounts.AccountOwnershipException;
import com.udea.digitalbank.shared.exception.customer.CustomerNotActiveException;
import com.udea.digitalbank.shared.exception.customer.CustomerNotFoundException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.support.HttpTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static com.udea.digitalbank.support.HttpTestSupport.authenticate;
import static com.udea.digitalbank.support.HttpTestSupport.body;
import static com.udea.digitalbank.support.HttpTestSupport.clearAuthentication;
import static com.udea.digitalbank.support.HttpTestSupport.get;
import static com.udea.digitalbank.support.HttpTestSupport.patch;
import static com.udea.digitalbank.support.HttpTestSupport.perform;
import static com.udea.digitalbank.support.HttpTestSupport.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas de contrato HTTP (AAA) de {@link AccountController}: qué código y qué mensaje recibe el cliente de la API
 * en los criterios de apertura, consulta, bloqueo y reactivación de cuentas.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU10 / HU12 / HU13 / HU15 / HU16 / HU28 - Contrato HTTP de cuentas")
class AccountControllerHttpTest {

    private static final String BASE = "/api/v1/accounts";
    private static final String NUMBER = "100000000001";

    @Mock
    private AccountOpeningService openingService;
    @Mock
    private AccountQueryService queryService;
    @Mock
    private AccountLifecycleService lifecycleService;

    private MockMvc mockMvc;
    private final UUID userId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = HttpTestSupport.mockMvc(new AccountController(openingService, queryService, lifecycleService));
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    private static AccountResponse account(String status) {
        AccountResponse response = new AccountResponse();
        response.setId(UUID.randomUUID());
        response.setAccountNumber(NUMBER);
        response.setAccountKey("@MA1231");
        response.setAccountType("SAVINGS");
        response.setStatus(status);
        response.setBalance(new BigDecimal("0.00"));
        return response;
    }

    private String openBody(String type) {
        return "{\"customerId\":\"" + customerId + "\",\"accountType\":\"" + type + "\"}";
    }

    @Nested
    @DisplayName("HU10 / HU28 - POST /accounts")
    class Open {

        @Test
        @DisplayName("HU28 CA01 / HU28 CA06 - El administrador crea la cuenta: 201, Activa y con saldo 0")
        void deberiaCrearLaCuenta() throws Exception {
            // Arrange
            authenticate(userId, true);
            when(openingService.open(new Actor(userId, true), customerId, AccountTypeEnum.SAVINGS))
                    .thenReturn(account("ACTIVE"));

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE, openBody("SAVINGS")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(201);
            assertThat(body(response)).contains("\"status\":\"ACTIVE\"").contains("\"balance\":0.00")
                    .contains("@MA1231");
        }

        @Test
        @DisplayName("HU10 CA01 / HU10 CA02 - El cliente abre su cuenta sin cuerpo ni depósito inicial: 201")
        void deberiaAbrirLaCuentaDelClienteSinCuerpo() throws Exception {
            // Arrange
            authenticate(userId, false);
            when(openingService.open(new Actor(userId, false), null, null)).thenReturn(account("ACTIVE"));

            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(BASE));

            // Assert
            assertThat(response.getStatus()).isEqualTo(201);
        }

        @Test
        @DisplayName("HU28 CA05 - Un tipo de cuenta inválido: 400 y no se crea ninguna cuenta")
        void deberiaRechazarUnTipoInvalido() throws Exception {
            // Arrange
            authenticate(userId, true);

            // Act
            MockHttpServletResponse investment = perform(mockMvc, post(BASE, openBody("INVERSION")));
            MockHttpServletResponse cdt = perform(mockMvc, post(BASE, openBody("CDT")));

            // Assert
            assertThat(investment.getStatus()).isEqualTo(400);
            assertThat(cdt.getStatus()).isEqualTo(400);
            verifyNoInteractions(openingService);
        }

        @Test
        @DisplayName("HU28 CA02 - Cliente inexistente: 404 que informa que el cliente no existe")
        void deberiaInformarQueElClienteNoExiste() throws Exception {
            // Arrange
            authenticate(userId, true);
            when(openingService.open(any(Actor.class), eq(customerId), eq(AccountTypeEnum.SAVINGS)))
                    .thenThrow(new CustomerNotFoundException("Cliente no encontrado: " + customerId));

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE, openBody("SAVINGS")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(body(response)).contains("Cliente no encontrado");
        }

        @Test
        @DisplayName("HU28 CA03 - Cliente inactivo: 403 que informa que el cliente no está activo")
        void deberiaRechazarAUnClienteInactivo() throws Exception {
            // Arrange
            authenticate(userId, true);
            when(openingService.open(any(Actor.class), eq(customerId), eq(AccountTypeEnum.SAVINGS)))
                    .thenThrow(new CustomerNotActiveException("El cliente no está activo: " + customerId));

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE, openBody("SAVINGS")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(body(response)).contains("no está activo");
        }

        @Test
        @DisplayName("HU10 CA03 / HU28 CA04 - Límite de tres cuentas: 409 que informa el máximo permitido")
        void deberiaInformarElLimiteDeCuentas() throws Exception {
            // Arrange
            authenticate(userId, true);
            when(openingService.open(any(Actor.class), eq(customerId), eq(AccountTypeEnum.SAVINGS)))
                    .thenThrow(new AccountLimitReachedException(
                            "El cliente ya alcanzó el número máximo permitido de 3 cuentas abiertas"));

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE, openBody("SAVINGS")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(body(response)).contains("número máximo permitido");
        }

        @Test
        @DisplayName("HU28 CA08 - Un cliente que intenta crear la cuenta de otro: 403")
        void deberiaRechazarLaCuentaDeOtroCliente() throws Exception {
            // Arrange
            authenticate(userId, false);
            when(openingService.open(new Actor(userId, false), customerId, AccountTypeEnum.SAVINGS))
                    .thenThrow(new AccountOwnershipException("No puedes abrir una cuenta a nombre de otro cliente"));

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE, openBody("SAVINGS")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("HU12 / HU13 - GET /accounts/{numero} y /balance")
    class Query {

        @Test
        @DisplayName("HU12 CA01 / HU12 CA04 - Saldo cero: 200 con saldo 0, no es un error")
        void deberiaDevolverSaldoCero() throws Exception {
            // Arrange
            authenticate(userId, false);
            AccountBalanceResponse balance = new AccountBalanceResponse();
            balance.setAccountNumber(NUMBER);
            balance.setBalance(new BigDecimal("0.00"));
            balance.setStatus("ACTIVE");
            when(queryService.getBalance(new Actor(userId, false), NUMBER)).thenReturn(balance);

            // Act
            MockHttpServletResponse response = perform(mockMvc, get(BASE + "/" + NUMBER + "/balance"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body(response)).contains("\"balance\":0.00");
        }

        @Test
        @DisplayName("HU12 CA02 / HU12 CA03 - Cuenta inexistente o ajena: 404 sin datos de la cuenta")
        void deberiaResponder404SinDatos() throws Exception {
            // Arrange
            authenticate(userId, false);
            when(queryService.getBalance(any(Actor.class), anyString()))
                    .thenThrow(new AccountNotFoundException("Cuenta no encontrada: " + NUMBER));

            // Act
            MockHttpServletResponse response = perform(mockMvc, get(BASE + "/" + NUMBER + "/balance"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(body(response)).contains("Cuenta no encontrada").doesNotContain("balance");
        }

        @Test
        @DisplayName("HU13 CA04 - Detalle de una cuenta bloqueada: 200 con el estado BLOCKED")
        void deberiaMostrarElEstadoBloqueado() throws Exception {
            // Arrange
            authenticate(userId, false);
            when(queryService.getAccount(new Actor(userId, false), NUMBER)).thenReturn(account("BLOCKED"));

            // Act
            MockHttpServletResponse response = perform(mockMvc, get(BASE + "/" + NUMBER));

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body(response)).contains("\"status\":\"BLOCKED\"");
        }

        @Test
        @DisplayName("HU13 CA02 / HU13 CA03 - Detalle de una cuenta inexistente o ajena: 404")
        void deberiaResponder404EnElDetalle() throws Exception {
            // Arrange
            authenticate(userId, false);
            when(queryService.getAccount(any(Actor.class), anyString()))
                    .thenThrow(new AccountNotFoundException("Cuenta no encontrada: " + NUMBER));

            // Act
            MockHttpServletResponse response = perform(mockMvc, get(BASE + "/" + NUMBER));

            // Assert
            assertThat(response.getStatus()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("HU15 / HU16 - PATCH /accounts/{numero}/block y /unblock")
    class Lifecycle {

        @Test
        @DisplayName("HU15 CA01 - Bloqueo con motivo: 200 con la cuenta BLOCKED y el administrador del token")
        void deberiaBloquearLaCuenta() throws Exception {
            // Arrange
            authenticate(userId, true);
            when(lifecycleService.blockAccount(NUMBER, "Alerta de fraude", userId)).thenReturn(account("BLOCKED"));

            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    patch(BASE + "/" + NUMBER + "/block", "{\"reason\":\"Alerta de fraude\"}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body(response)).contains("\"status\":\"BLOCKED\"");
        }

        @Test
        @DisplayName("HU15 CA02 - Cuenta ya bloqueada: 409 que informa que ya está bloqueada")
        void deberiaInformarQueYaEstaBloqueada() throws Exception {
            // Arrange
            authenticate(userId, true);
            when(lifecycleService.blockAccount(NUMBER, "Alerta repetida", userId))
                    .thenThrow(new AccountNotBlockableException("La cuenta " + NUMBER + " ya está bloqueada"));

            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    patch(BASE + "/" + NUMBER + "/block", "{\"reason\":\"Alerta repetida\"}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(body(response)).contains("ya está bloqueada");
        }

        @Test
        @DisplayName("HU15 CA03 - Bloqueo sin motivo: 400 que indica que el motivo es obligatorio")
        void deberiaExigirElMotivoDelBloqueo() throws Exception {
            // Arrange
            authenticate(userId, true);

            // Act
            MockHttpServletResponse response = perform(mockMvc, patch(BASE + "/" + NUMBER + "/block", "{}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(body(response)).contains("El motivo del bloqueo es obligatorio");
            verifyNoInteractions(lifecycleService);
        }

        @Test
        @DisplayName("HU16 CA01 - Reactivación con motivo: 200 con la cuenta ACTIVE")
        void deberiaReactivarLaCuenta() throws Exception {
            // Arrange
            authenticate(userId, true);
            when(lifecycleService.unblockAccount(NUMBER, "Identidad verificada", userId))
                    .thenReturn(account("ACTIVE"));

            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    patch(BASE + "/" + NUMBER + "/unblock", "{\"reason\":\"Identidad verificada\"}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body(response)).contains("\"status\":\"ACTIVE\"");
            verify(lifecycleService).unblockAccount(NUMBER, "Identidad verificada", userId);
        }

        @Test
        @DisplayName("HU16 CA02 - Reactivación con motivo en blanco: 400 y la cuenta no se toca")
        void deberiaExigirElMotivoDeLaReactivacion() throws Exception {
            // Arrange
            authenticate(userId, true);

            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    patch(BASE + "/" + NUMBER + "/unblock", "{\"reason\":\"   \"}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(body(response)).contains("El motivo del desbloqueo es obligatorio");
            verifyNoInteractions(lifecycleService);
        }

        @Test
        @DisplayName("HU16 CA03 - Reactivar una cuenta activa: 409 que informa que no está bloqueada")
        void deberiaInformarQueLaCuentaNoEstaBloqueada() throws Exception {
            // Arrange
            authenticate(userId, true);
            when(lifecycleService.unblockAccount(NUMBER, "Revisión", userId)).thenThrow(
                    new AccountNotUnblockableException("Solo se puede desbloquear una cuenta bloqueada; la cuenta "
                            + NUMBER + " está en estado ACTIVE"));

            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    patch(BASE + "/" + NUMBER + "/unblock", "{\"reason\":\"Revisión\"}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(body(response)).contains("Solo se puede desbloquear una cuenta bloqueada");
        }
    }
}
