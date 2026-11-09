package com.udea.digitalbank.accounts.controller;

import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.service.AccountLifecycleService;
import com.udea.digitalbank.accounts.service.AccountOpeningService;
import com.udea.digitalbank.accounts.service.AccountQueryService;
import com.udea.digitalbank.support.HttpTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.udea.digitalbank.support.HttpTestSupport.authenticate;
import static com.udea.digitalbank.support.HttpTestSupport.body;
import static com.udea.digitalbank.support.HttpTestSupport.clearAuthentication;
import static com.udea.digitalbank.support.HttpTestSupport.get;
import static com.udea.digitalbank.support.HttpTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas de contrato HTTP (AAA) de GET /api/v1/accounts, el listado del cliente autenticado (HU11): qué código y qué
 * cuerpo recibe, y que la consulta no deja pedir las cuentas de otro cliente. El 401 sin token ya lo cubre
 * ApiAuthorizationTest. Aquí el servicio está simulado: que el cliente solo vea las cuentas ACTIVE y BLOCKED se
 * prueba con el servicio real en AccountListHuTest, también por HTTP.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU11 - Contrato HTTP de GET /accounts")
class AccountListHttpTest {

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

    @Test
    @DisplayName("HU11 CA01 - 200 con la lista de cuentas del cliente autenticado")
    void deberiaListarLasCuentasDelCliente() throws Exception {
        // Arrange
        authenticate(userId, false);
        when(queryService.getMyAccounts(userId)).thenReturn(List.of(account("ACTIVE"), account("BLOCKED")));

        // Act
        MockHttpServletResponse response = perform(mockMvc, get(BASE));

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response)).startsWith("[").endsWith("]")
                .contains("\"status\":\"ACTIVE\"", "\"status\":\"BLOCKED\"", "\"accountNumber\":\"" + NUMBER + "\"");
        verify(queryService).getMyAccounts(userId);
    }

    @Test
    @DisplayName("HU11 CA02 - Cliente sin cuentas: 200 con [] y no un error")
    void deberiaDevolverListaVacia() throws Exception {
        // Arrange
        authenticate(userId, false);
        when(queryService.getMyAccounts(userId)).thenReturn(List.of());

        // Act
        MockHttpServletResponse response = perform(mockMvc, get(BASE));

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response)).isEqualTo("[]");
    }

    @Test
    @DisplayName("HU11 CA03 - Un customerId en la consulta se ignora: se listan las cuentas del userId del token")
    void deberiaIgnorarUnCustomerIdDeLaConsulta() throws Exception {
        // Arrange
        authenticate(userId, false);
        UUID otherCustomer = UUID.randomUUID();
        when(queryService.getMyAccounts(userId)).thenReturn(List.of());

        // Act
        MockHttpServletResponse response = perform(mockMvc,
                get(BASE + "?customerId=" + otherCustomer + "&userId=" + otherCustomer));

        // Assert: la única llamada es con el userId del token; el identificador de la consulta no llega al servicio
        assertThat(response.getStatus()).isEqualTo(200);
        verify(queryService).getMyAccounts(userId);
        verify(queryService, never()).getMyAccounts(otherCustomer);
        verifyNoMoreInteractions(queryService);
    }
}
