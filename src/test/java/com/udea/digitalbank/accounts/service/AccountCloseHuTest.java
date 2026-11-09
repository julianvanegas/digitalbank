package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.controller.AccountController;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.domain.AccountAction;
import com.udea.digitalbank.accounts.domain.AccountActionType;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.mapper.AccountMapper;
import com.udea.digitalbank.accounts.repository.AccountActionRepository;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.shared.exception.accounts.AccountNotClosableException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.support.HttpTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static com.udea.digitalbank.accounts.service.AccountFixtures.ACCOUNT_NUMBER;
import static com.udea.digitalbank.accounts.service.AccountFixtures.buildAccount;
import static com.udea.digitalbank.accounts.service.AccountFixtures.status;
import static com.udea.digitalbank.support.HttpTestSupport.authenticate;
import static com.udea.digitalbank.support.HttpTestSupport.body;
import static com.udea.digitalbank.support.HttpTestSupport.clearAuthentication;
import static com.udea.digitalbank.support.HttpTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link AccountLifecycleService#closeAccount} para HU17 por el camino del titular
 * ("Como cliente titular de la cuenta"). Las de AccountLifecycleServiceTest cubren las mismas reglas, pero el rechazo
 * por saldo y el de cuenta bloqueada pasan por un administrador. AccountLookup y el mapper son reales sobre los mocks.
 * Incluye el contrato HTTP del cierre, con el servicio real (ver el comentario del @Nested CloseHttp).
 * HU17 CA03 no tiene prueba: no es alcanzable en este modelo (ver el Javadoc de Account.close).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU17 - Cerrar cuenta")
class AccountCloseHuTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountActionRepository accountActionRepository;
    @Mock
    private CustomerApi customerApi;
    @Mock
    private AccountCatalog accountCatalog;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AccountLifecycleService lifecycleService;
    private final UUID userId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final Actor owner = new Actor(userId, false);

    @BeforeEach
    void setUp() {
        lifecycleService = new AccountLifecycleService(accountRepository, accountActionRepository, customerApi,
                Mappers.getMapper(AccountMapper.class), new AccountLookup(accountRepository), accountCatalog,
                eventPublisher);
        CustomerView customer = new CustomerView(customerId, userId, "Ana", "Gómez",
                "Cédula de ciudadanía", "1000000001", "ana@example.com", "ACTIVE");
        when(customerApi.getCustomerByUserId(userId)).thenReturn(customer);
    }

    private Account ownedAccount(AccountStatusEnum status, String balance) {
        Account account = buildAccount(status);
        ReflectionTestUtils.setField(account, "customerId", customerId);
        ReflectionTestUtils.setField(account, "balance", new BigDecimal(balance));
        when(accountRepository.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));
        return account;
    }

    @Test
    @DisplayName("HU17 CA01 - El titular cierra su cuenta activa con saldo cero: queda CLOSED, con fecha, acción registrada y aviso al titular")
    void titularDeberiaCerrarSuCuentaActivaSinSaldo() {
        // Arrange
        Account account = ownedAccount(AccountStatusEnum.ACTIVE, "0.00");
        when(accountCatalog.status(AccountStatusEnum.CLOSED)).thenReturn(status(AccountStatusEnum.CLOSED));
        when(accountRepository.save(account)).thenReturn(account);

        // Act
        AccountResponse response = lifecycleService.closeAccount(owner, ACCOUNT_NUMBER);

        // Assert: la cuenta queda cerrada con su fecha
        assertThat(account.is(AccountStatusEnum.CLOSED)).isTrue();
        assertThat(account.getClosedAt()).isNotNull();
        assertThat(response.getStatus()).isEqualTo("CLOSED");
        assertThat(response.getClosedAt()).isEqualTo(account.getClosedAt());

        // Assert: la acción queda registrada con el motivo del cliente y el titular como autor
        ArgumentCaptor<AccountAction> action = ArgumentCaptor.forClass(AccountAction.class);
        verify(accountActionRepository).save(action.capture());
        assertThat(action.getValue().getAction()).isEqualTo(AccountActionType.CLOSED);
        assertThat(action.getValue().getReason()).isEqualTo(AccountLifecycleService.CLOSED_BY_CUSTOMER);
        assertThat(action.getValue().getCreatedBy()).isEqualTo(userId);

        // Assert: se emite la confirmación al titular (el evento que dispara el aviso por correo)
        ArgumentCaptor<AccountActionEvent> event = ArgumentCaptor.forClass(AccountActionEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AccountActionType.CLOSED);
        assertThat(event.getValue().customerId()).isEqualTo(customerId);
        assertThat(event.getValue().accountNumber()).isEqualTo(ACCOUNT_NUMBER);
        assertThat(event.getValue().reason()).isEqualTo(AccountLifecycleService.CLOSED_BY_CUSTOMER);
    }

    @Test
    @DisplayName("HU17 CA02 - El titular no puede cerrar una cuenta con saldo: se le indica retirar o transferir los fondos")
    void titularNoDeberiaCerrarUnaCuentaConSaldo() {
        // Arrange
        Account account = ownedAccount(AccountStatusEnum.ACTIVE, "0.01");

        // Act & Assert
        assertThatThrownBy(() -> lifecycleService.closeAccount(owner, ACCOUNT_NUMBER))
                .isInstanceOf(AccountNotClosableException.class)
                .hasMessage("La cuenta " + ACCOUNT_NUMBER
                        + " tiene saldo; debe retirar o transferir la totalidad de los fondos antes de cerrarla");
        assertThat(account.is(AccountStatusEnum.ACTIVE)).isTrue();
        assertThat(account.getBalance()).isEqualByComparingTo("0.01");
        assertThat(account.getClosedAt()).isNull();
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(accountActionRepository, eventPublisher);
    }

    @Test
    @DisplayName("HU17 CA01 - El titular no puede cerrar una cuenta bloqueada: el criterio exige que no tenga bloqueos")
    void titularNoDeberiaCerrarUnaCuentaBloqueada() {
        // Arrange
        Account account = ownedAccount(AccountStatusEnum.BLOCKED, "0.00");

        // Act & Assert
        assertThatThrownBy(() -> lifecycleService.closeAccount(owner, ACCOUNT_NUMBER))
                .isInstanceOf(AccountNotClosableException.class)
                .hasMessageContaining("bloqueada");
        assertThat(account.is(AccountStatusEnum.BLOCKED)).isTrue();
        assertThat(account.getClosedAt()).isNull();
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(accountActionRepository, eventPublisher);
    }

    /*
     * Contrato HTTP de PATCH /api/v1/accounts/{numero}/close. Vive en esta clase del paquete accounts.service, y no en
     * una de accounts.controller, porque necesita el AccountLifecycleService real detrás del controller, y para
     * construirlo hacen falta AccountLookup y AccountCatalog, que son de paquete. Con el servicio simulado, el mensaje
     * y las reglas los escribiría la propia prueba en el thenThrow: no comprobaría Account.java ni AccountLookup, y
     * seguiría pasando aunque alguien revirtiera el cambio. Controller y servicio son reales; repositorios, catálogo,
     * customerApi y el publicador de eventos, simulados.
     */
    @Nested
    @DisplayName("HU17 - Contrato HTTP de PATCH /accounts/{numero}/close")
    class CloseHttp {

        private static final String CLOSE = "/api/v1/accounts/" + ACCOUNT_NUMBER + "/close";

        private MockMvc mockMvc;

        @BeforeEach
        void setUpHttp() {
            mockMvc = HttpTestSupport.mockMvc(new AccountController(
                    mock(AccountOpeningService.class), mock(AccountQueryService.class), lifecycleService));
            authenticate(userId, false);
        }

        @AfterEach
        void tearDownHttp() {
            clearAuthentication();
        }

        @Test
        @DisplayName("HU17 CA01 - El titular cierra su cuenta activa con saldo cero: 200 con la cuenta CLOSED")
        void deberiaResponder200AlCerrar() throws Exception {
            // Arrange
            Account account = ownedAccount(AccountStatusEnum.ACTIVE, "0.00");
            when(accountCatalog.status(AccountStatusEnum.CLOSED)).thenReturn(status(AccountStatusEnum.CLOSED));
            when(accountRepository.save(account)).thenReturn(account);

            // Act
            MockHttpServletResponse response = perform(mockMvc, MockMvcRequestBuilders.patch(CLOSE));

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body(response)).contains("\"accountNumber\":\"" + ACCOUNT_NUMBER + "\"", "\"status\":\"CLOSED\"");
            assertThat(account.is(AccountStatusEnum.CLOSED)).isTrue();
        }

        @Test
        @DisplayName("HU17 CA02 - Cuenta con saldo: 409 que indica retirar o transferir la totalidad de los fondos")
        void deberiaResponder409ConSaldo() throws Exception {
            // Arrange
            Account account = ownedAccount(AccountStatusEnum.ACTIVE, "10.00");

            // Act
            MockHttpServletResponse response = perform(mockMvc, MockMvcRequestBuilders.patch(CLOSE));

            // Assert: el mensaje sale de Account.close, no de la prueba
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(body(response)).contains("La cuenta " + ACCOUNT_NUMBER
                    + " tiene saldo; debe retirar o transferir la totalidad de los fondos antes de cerrarla");
            assertThat(account.is(AccountStatusEnum.ACTIVE)).isTrue();
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("HU17 CA01 - Cuenta bloqueada: 409 que indica desbloquearla antes de cerrarla")
        void deberiaResponder409Bloqueada() throws Exception {
            // Arrange
            Account account = ownedAccount(AccountStatusEnum.BLOCKED, "0.00");

            // Act
            MockHttpServletResponse response = perform(mockMvc, MockMvcRequestBuilders.patch(CLOSE));

            // Assert
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(body(response)).contains("bloqueada");
            assertThat(account.is(AccountStatusEnum.BLOCKED)).isTrue();
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("HU17 - Cuenta inexistente o de otro cliente: 404 con el mismo mensaje y sin cerrar nada")
        void deberiaResponder404InexistenteOAjena() throws Exception {
            // Arrange: el mismo número, primero sin cuenta y luego con una cuenta de otro cliente
            Account foreign = buildAccount(AccountStatusEnum.ACTIVE);
            ReflectionTestUtils.setField(foreign, "balance", new BigDecimal("0.00"));
            when(accountRepository.findByAccountNumberForUpdate(ACCOUNT_NUMBER))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(foreign));

            // Act
            MockHttpServletResponse missing = perform(mockMvc, MockMvcRequestBuilders.patch(CLOSE));
            MockHttpServletResponse notMine = perform(mockMvc, MockMvcRequestBuilders.patch(CLOSE));

            // Assert: las dos responden igual, así que el 404 no revela si el número existe
            assertThat(missing.getStatus()).isEqualTo(404);
            assertThat(notMine.getStatus()).isEqualTo(404);
            assertThat(body(missing)).contains("Cuenta no encontrada: " + ACCOUNT_NUMBER);
            assertThat(body(notMine)).contains("Cuenta no encontrada: " + ACCOUNT_NUMBER);
            assertThat(foreign.is(AccountStatusEnum.ACTIVE)).isTrue();
            verify(accountRepository, never()).save(any());
            verifyNoInteractions(accountActionRepository, eventPublisher);
        }
    }
}
