package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.controller.AccountController;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.mapper.AccountMapper;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.support.HttpTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.udea.digitalbank.accounts.service.AccountFixtures.buildAccount;
import static com.udea.digitalbank.support.HttpTestSupport.authenticate;
import static com.udea.digitalbank.support.HttpTestSupport.body;
import static com.udea.digitalbank.support.HttpTestSupport.clearAuthentication;
import static com.udea.digitalbank.support.HttpTestSupport.get;
import static com.udea.digitalbank.support.HttpTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link AccountQueryService#getMyAccounts} para los criterios de HU11 que no tienen
 * prueba en AccountQueryServiceTest: varias cuentas en orden, que el cliente solo ve las ACTIVE y BLOCKED (alcance
 * confirmado por el dueño del producto), lista vacía, que el cliente sale solo del token y que consultar no modifica
 * nada. AccountLookup y el mapper son reales sobre los mismos mocks.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU11 - Consultar cuentas del cliente")
class AccountListHuTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerApi customerApi;

    private AccountQueryService queryService;
    private final UUID userId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        queryService = new AccountQueryService(accountRepository, customerApi,
                Mappers.getMapper(AccountMapper.class), new AccountLookup(accountRepository));
    }

    private void stubCustomer() {
        CustomerView customer = new CustomerView(customerId, userId, "Ana", "Gómez",
                "Cédula de ciudadanía", "1000000001", "ana@example.com", "ACTIVE");
        when(customerApi.getCustomerByUserId(userId)).thenReturn(customer);
    }

    private Account accountOf(String accountNumber, AccountStatusEnum status) {
        Account account = buildAccount(status);
        ReflectionTestUtils.setField(account, "customerId", customerId);
        ReflectionTestUtils.setField(account, "accountNumber", accountNumber);
        return account;
    }

    // Con el repositorio simulado solo se demuestra que el servicio conserva el orden que recibe. El orden real de
    // apertura lo garantiza el ORDER BY created_at de la consulta (findByCustomerIdOrderByCreatedAt), que aquí no
    // se ejecuta: probarlo exigiría una base de datos real.
    @Test
    @DisplayName("HU11 CA01 - Con varias cuentas las devuelve todas, en el orden en que llegan de la consulta")
    void deberiaListarVariasCuentasConservandoElOrden() {
        // Arrange
        stubCustomer();
        when(accountRepository.findByCustomerIdOrderByCreatedAt(customerId)).thenReturn(List.of(
                accountOf("100000000001", AccountStatusEnum.ACTIVE),
                accountOf("100000000002", AccountStatusEnum.ACTIVE),
                accountOf("100000000003", AccountStatusEnum.ACTIVE)));

        // Act
        List<AccountResponse> accounts = queryService.getMyAccounts(userId);

        // Assert
        assertThat(accounts).extracting(AccountResponse::getAccountNumber)
                .containsExactly("100000000001", "100000000002", "100000000003");
    }

    @Test
    @DisplayName("HU11 CA01 - Con cuentas en los cuatro estados, el cliente recibe solo la ACTIVE y la BLOCKED, en orden")
    void deberiaMostrarSoloActivasYBloqueadas() {
        // Arrange
        stubCustomer();
        when(accountRepository.findByCustomerIdOrderByCreatedAt(customerId)).thenReturn(List.of(
                accountOf("100000000001", AccountStatusEnum.CLOSED),
                accountOf("100000000002", AccountStatusEnum.ACTIVE),
                accountOf("100000000003", AccountStatusEnum.INACTIVE),
                accountOf("100000000004", AccountStatusEnum.BLOCKED)));

        // Act
        List<AccountResponse> accounts = queryService.getMyAccounts(userId);

        // Assert
        assertThat(accounts).extracting(AccountResponse::getAccountNumber, AccountResponse::getStatus)
                .containsExactly(tuple("100000000002", "ACTIVE"), tuple("100000000004", "BLOCKED"));
    }

    @Test
    @DisplayName("HU11 CA01 - Un cliente con todas sus cuentas cerradas recibe una lista vacía, sin excepción")
    void deberiaDevolverListaVaciaSiTodasEstanCerradas() {
        // Arrange
        stubCustomer();
        when(accountRepository.findByCustomerIdOrderByCreatedAt(customerId)).thenReturn(List.of(
                accountOf("100000000001", AccountStatusEnum.CLOSED),
                accountOf("100000000002", AccountStatusEnum.CLOSED)));

        // Act & Assert
        assertThatCode(() -> assertThat(queryService.getMyAccounts(userId)).isNotNull().isEmpty())
                .doesNotThrowAnyException();
    }

    /*
     * Contrato HTTP del filtro. Va aquí y no en AccountListHttpTest porque necesita el AccountQueryService real detrás
     * del controller (allí el servicio está simulado y no demostraría nada), y AccountLookup solo se puede construir
     * desde este paquete. Controller y servicio son reales; el repositorio y customerApi, simulados.
     */
    @Test
    @DisplayName("HU11 CA01 - GET /accounts: 200 solo con las cuentas ACTIVE y BLOCKED del cliente")
    void getAccounts_deberiaResponderSoloActivasYBloqueadas() throws Exception {
        // Arrange
        stubCustomer();
        when(accountRepository.findByCustomerIdOrderByCreatedAt(customerId)).thenReturn(List.of(
                accountOf("100000000001", AccountStatusEnum.CLOSED),
                accountOf("100000000002", AccountStatusEnum.ACTIVE),
                accountOf("100000000003", AccountStatusEnum.INACTIVE),
                accountOf("100000000004", AccountStatusEnum.BLOCKED)));
        MockMvc mockMvc = HttpTestSupport.mockMvc(new AccountController(
                mock(AccountOpeningService.class), queryService, mock(AccountLifecycleService.class)));
        authenticate(userId, false);

        try {
            // Act
            MockHttpServletResponse response = perform(mockMvc, get("/api/v1/accounts"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body(response))
                    .contains("\"accountNumber\":\"100000000002\"", "\"accountNumber\":\"100000000004\"",
                            "\"status\":\"ACTIVE\"", "\"status\":\"BLOCKED\"")
                    .doesNotContain("100000000001", "100000000003", "\"status\":\"CLOSED\"", "\"status\":\"INACTIVE\"");
        } finally {
            clearAuthentication();
        }
    }

    @Test
    @DisplayName("HU11 CA02 - Un cliente sin cuentas recibe una lista vacía, sin excepción")
    void deberiaDevolverListaVaciaSinError() {
        // Arrange
        stubCustomer();
        when(accountRepository.findByCustomerIdOrderByCreatedAt(customerId)).thenReturn(List.of());

        // Act & Assert
        assertThatCode(() -> assertThat(queryService.getMyAccounts(userId)).isNotNull().isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HU11 CA03 - El cliente se resuelve solo desde el userId del token y se consultan solo sus cuentas")
    void deberiaResolverElClienteDesdeElUserIdDelToken() {
        // Arrange
        stubCustomer();
        when(accountRepository.findByCustomerIdOrderByCreatedAt(customerId))
                .thenReturn(List.of(accountOf("100000000001", AccountStatusEnum.ACTIVE)));

        // Act
        List<AccountResponse> accounts = queryService.getMyAccounts(userId);

        // Assert
        assertThat(accounts).extracting(AccountResponse::getCustomerId).containsOnly(customerId);
        // Una sola resolución de cliente, desde el userId, y una sola consulta, con el cliente resuelto: este camino
        // no busca por documento ni recibe ningún otro identificador de cliente
        verify(customerApi).getCustomerByUserId(userId);
        verify(accountRepository).findByCustomerIdOrderByCreatedAt(customerId);
        verifyNoMoreInteractions(customerApi, accountRepository);
    }

    /*
     * La garantía real de que la consulta no modifica nada es @Transactional(readOnly = true): sin él, un cambio
     * sobre una entidad leída se escribiría al confirmar aunque nadie llame a save. Un mock no detecta eso, así que
     * la prueba comprueba también la anotación, para que nadie la quite sin darse cuenta.
     */
    @Test
    @DisplayName("HU11 CA04 - El listado no guarda ni modifica nada y el método es de solo lectura")
    void noDeberiaModificarNada() throws NoSuchMethodException {
        // Arrange
        stubCustomer();
        Account account = accountOf("100000000001", AccountStatusEnum.ACTIVE);
        when(accountRepository.findByCustomerIdOrderByCreatedAt(customerId)).thenReturn(List.of(account));

        // Act
        queryService.getMyAccounts(userId);

        // Assert
        assertThat(account.is(AccountStatusEnum.ACTIVE)).isTrue();
        assertThat(account.getBalance()).isEqualByComparingTo("150.75");
        assertThat(account.getClosedAt()).isNull();
        verify(accountRepository).findByCustomerIdOrderByCreatedAt(customerId);
        verify(accountRepository, never()).save(any());
        verifyNoMoreInteractions(accountRepository);
        Transactional tx = AccountQueryService.class.getMethod("getMyAccounts", UUID.class)
                .getAnnotation(Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.readOnly()).isTrue();
    }
}
