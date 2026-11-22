package com.udea.digitalbank.transactions.controller;

import com.udea.digitalbank.shared.exception.transactions.LimitsNotDefinedException;
import com.udea.digitalbank.support.HttpTestSupport;
import com.udea.digitalbank.transactions.dto.TransactionLimitResponse;
import com.udea.digitalbank.transactions.service.TransactionLimitQueryService;
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
import static org.mockito.Mockito.when;

/**
 * Pruebas de contrato HTTP (AAA) de {@link TransactionLimitController}: qué recibe quien consulta los topes. Quién
 * puede llamar a la ruta lo comprueba {@code ApiAuthorizationTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Contrato HTTP de la consulta de topes")
class TransactionLimitControllerHttpTest {

    private static final String LIMITS = "/api/v1/transactions/limits";

    @Mock
    private TransactionLimitQueryService limitService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = HttpTestSupport.mockMvc(new TransactionLimitController(limitService));
        authenticate(UUID.randomUUID(), false);
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    private static TransactionLimitResponse withdrawal() {
        TransactionLimitResponse response = new TransactionLimitResponse();
        response.setType("WITHDRAWAL");
        response.setAccountType("SAVINGS");
        response.setCurrency("COP");
        response.setMaxDailyCount(4);
        response.setMaxPerTransaction(new BigDecimal("2500000.00"));
        response.setMaxDailyAmount(new BigDecimal("10000000.00"));
        response.setRequiresTwoFactor(true);
        return response;
    }

    private static TransactionLimitResponse transfer() {
        TransactionLimitResponse response = new TransactionLimitResponse();
        response.setType("TRANSFER");
        response.setAccountType("SAVINGS");
        response.setCurrency("COP");
        response.setMaxDailyAmount(new BigDecimal("80000000.00"));
        response.setRequiresTwoFactor(true);
        return response;
    }

    @Test
    @DisplayName("Sin tipo: 200 con los topes de retiros y de transferencias; los topes que no aplican no viajan")
    void deberiaDevolverLosTopesDeTodosLosMovimientos() throws Exception {
        // Arrange
        when(limitService.getLimits(null, null)).thenReturn(List.of(transfer(), withdrawal()));

        // Act
        MockHttpServletResponse response = perform(mockMvc, get(LIMITS));

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response))
                .contains("\"type\":\"TRANSFER\"", "\"type\":\"WITHDRAWAL\"", "\"maxDailyCount\":4",
                        "\"maxDailyAmount\":80000000.00", "\"requiresTwoFactor\":true", "\"currency\":\"COP\"")
                .doesNotContain("null");
    }

    @Test
    @DisplayName("Con tipo y tipo de cuenta: los pasa al servicio tal como llegan")
    void deberiaPasarLosFiltrosAlServicio() throws Exception {
        // Arrange
        when(limitService.getLimits("WITHDRAWAL", "SAVINGS")).thenReturn(List.of(withdrawal()));

        // Act
        MockHttpServletResponse response = perform(mockMvc, get(LIMITS + "?type=WITHDRAWAL&accountType=SAVINGS"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response)).contains("\"maxPerTransaction\":2500000.00");
    }

    @Test
    @DisplayName("Un tipo sin topes definidos: 404 que informa que no existen topes para ese tipo de operación")
    void deberiaResponder404ParaUnTipoSinTopes() throws Exception {
        // Arrange
        when(limitService.getLimits("DEPOSIT", null))
                .thenThrow(new LimitsNotDefinedException("No existen topes definidos para el tipo de operación DEPOSIT"));

        // Act
        MockHttpServletResponse response = perform(mockMvc, get(LIMITS + "?type=DEPOSIT"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(body(response)).contains("No existen topes definidos para el tipo de operación DEPOSIT");
    }
}
