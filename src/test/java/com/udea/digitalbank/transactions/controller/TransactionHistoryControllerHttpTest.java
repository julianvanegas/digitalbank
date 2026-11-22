package com.udea.digitalbank.transactions.controller;

import com.udea.digitalbank.shared.exception.InvalidRequestException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.shared.exception.GlobalExceptionHandler;
import com.udea.digitalbank.shared.security.ActorArgumentResolver;
import com.udea.digitalbank.transactions.dto.TransactionHistoryEntry;
import com.udea.digitalbank.transactions.service.TransactionHistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.udea.digitalbank.support.HttpTestSupport.authenticate;
import static com.udea.digitalbank.support.HttpTestSupport.body;
import static com.udea.digitalbank.support.HttpTestSupport.clearAuthentication;
import static com.udea.digitalbank.support.HttpTestSupport.get;
import static com.udea.digitalbank.support.HttpTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas de contrato HTTP (AAA) de {@link TransactionHistoryController}: qué código y qué cuerpo recibe el cliente de
 * la API al consultar el historial de una cuenta.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Contrato HTTP del historial de transacciones")
class TransactionHistoryControllerHttpTest {

    private static final String HISTORY = "/api/v1/transactions";
    private static final String ACCOUNT = "100000000001";
    private static final String QUERY = "?accountNumber=" + ACCOUNT + "&from=2026-10-01&to=2026-10-31";

    @Mock
    private TransactionHistoryService historyService;

    private MockMvc mockMvc;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Como HttpTestSupport.mockMvc, más el resolvedor de Pageable que en la aplicación registra Spring Boot
        mockMvc = MockMvcBuilders.standaloneSetup(new TransactionHistoryController(historyService))
                .setControllerAdvice(new GlobalExceptionHandler(Clock.systemDefaultZone()))
                .setCustomArgumentResolvers(new ActorArgumentResolver(), new PageableHandlerMethodArgumentResolver())
                .build();
        authenticate(userId, false);
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    private static TransactionHistoryEntry entry(String type, String amount, String counterpart) {
        TransactionHistoryEntry entry = new TransactionHistoryEntry();
        entry.setReference("TRF-20261017-7Xk2Pq9RmZ4tNvB8cHdWeA");
        entry.setType(type);
        entry.setAmount(new BigDecimal(amount));
        entry.setCounterpartAccountNumber(counterpart);
        entry.setCreatedAt(LocalDateTime.of(2026, 10, 17, 10, 15, 30));
        return entry;
    }

    // Una página real, como la del repositorio: PageImpl sin Pageable (unpaged) no se puede serializar
    private static Page<TransactionHistoryEntry> pageOf(TransactionHistoryEntry... entries) {
        return new PageImpl<>(List.of(entries), PageRequest.of(0, 20), entries.length);
    }

    @Test
    @DisplayName("200 con los movimientos de la página: los egresos llegan con monto negativo")
    void deberiaResponderConLaPaginaDeMovimientos() throws Exception {
        when(historyService.getHistory(eq(new Actor(userId, false)), eq(ACCOUNT), eq(LocalDate.of(2026, 10, 1)),
                eq(LocalDate.of(2026, 10, 31)), any(Pageable.class)))
                .thenReturn(pageOf(entry("TRANSFER", "-150000.00", "200000000002"), entry("DEPOSIT", "50000.00", null)));

        MockHttpServletResponse response = perform(mockMvc, get(HISTORY + QUERY));

        assertThat(response.getStatus()).as(body(response)).isEqualTo(200);
        assertThat(body(response))
                .contains("\"amount\":-150000.00")
                .contains("\"amount\":50000.00")
                .contains("\"counterpartAccountNumber\":\"200000000002\"")
                .contains("\"type\":\"DEPOSIT\"");
    }

    @Test
    @DisplayName("Sin paginación pedida, usa la primera página de 20 movimientos")
    void deberiaUsarLaPaginaPorDefecto() throws Exception {
        when(historyService.getHistory(any(), any(), any(), any(), any())).thenReturn(pageOf());

        perform(mockMvc, get(HISTORY + QUERY));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(historyService).getHistory(any(), any(), any(), any(), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("Una cuenta sin movimientos en el rango responde 200 con la lista vacía, no un error")
    void deberiaResponderListaVaciaSinMovimientos() throws Exception {
        when(historyService.getHistory(any(), any(), any(), any(), any())).thenReturn(pageOf());

        MockHttpServletResponse response = perform(mockMvc, get(HISTORY + QUERY));

        assertThat(response.getStatus()).as(body(response)).isEqualTo(200);
        assertThat(body(response)).contains("\"content\":[]");
    }

    @Test
    @DisplayName("404 si la cuenta no existe o no es del usuario")
    void deberiaResponder404ConUnaCuentaAjena() throws Exception {
        when(historyService.getHistory(any(), any(), any(), any(), any()))
                .thenThrow(new AccountNotFoundException("Cuenta no encontrada: " + ACCOUNT));

        MockHttpServletResponse response = perform(mockMvc, get(HISTORY + QUERY));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(body(response)).contains("Cuenta no encontrada: " + ACCOUNT);
    }

    @Test
    @DisplayName("400 con el mensaje del servicio si la fecha inicial es posterior a la final")
    void deberiaResponder400ConUnRangoInvertido() throws Exception {
        when(historyService.getHistory(any(), any(), any(), any(), any()))
                .thenThrow(new InvalidRequestException("La fecha inicial no puede ser posterior a la fecha final"));

        MockHttpServletResponse response = perform(mockMvc, get(HISTORY + QUERY));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(body(response)).contains("La fecha inicial no puede ser posterior a la fecha final");
    }

    @Test
    @DisplayName("400 si falta la cuenta o alguna de las fechas")
    void deberiaResponder400SiFaltaUnParametro() throws Exception {
        for (String query : List.of("?from=2026-10-01&to=2026-10-31",
                "?accountNumber=" + ACCOUNT + "&to=2026-10-31",
                "?accountNumber=" + ACCOUNT + "&from=2026-10-01")) {
            assertThat(perform(mockMvc, get(HISTORY + query)).getStatus()).as(query).isEqualTo(400);
        }
        verifyNoInteractions(historyService);
    }

    @Test
    @DisplayName("400 si una fecha no tiene el formato yyyy-MM-dd")
    void deberiaResponder400ConUnaFechaMalFormada() throws Exception {
        for (String dates : List.of("from=01-10-2026&to=2026-10-31", "from=2026-10-01&to=2026-13-40",
                "from=2026-10-01T00:00:00&to=2026-10-31", "from=ayer&to=hoy")) {
            assertThat(perform(mockMvc, get(HISTORY + "?accountNumber=" + ACCOUNT + "&" + dates)).getStatus())
                    .as(dates).isEqualTo(400);
        }
        verifyNoInteractions(historyService);
    }

    @Test
    @DisplayName("El usuario que consulta sale del token y se entrega al servicio como Actor")
    void deberiaEntregarAlServicioElActorDelToken() throws Exception {
        UUID adminId = UUID.randomUUID();
        authenticate(adminId, true);
        when(historyService.getHistory(any(), any(), any(), any(), any())).thenReturn(pageOf());

        perform(mockMvc, get(HISTORY + QUERY));

        verify(historyService).getHistory(eq(new Actor(adminId, true)), eq(ACCOUNT), any(), any(), any());
    }
}
