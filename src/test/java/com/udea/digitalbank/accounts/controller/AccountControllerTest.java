package com.udea.digitalbank.accounts.controller;

import com.udea.digitalbank.accounts.api.AccountTypeEnum;
import com.udea.digitalbank.accounts.dto.AccountBalanceResponse;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.dto.BlockAccountRequest;
import com.udea.digitalbank.accounts.dto.CreateAccountRequest;
import com.udea.digitalbank.accounts.dto.UnblockAccountRequest;
import com.udea.digitalbank.accounts.service.AccountLifecycleService;
import com.udea.digitalbank.accounts.service.AccountOpeningService;
import com.udea.digitalbank.accounts.service.AccountQueryService;
import com.udea.digitalbank.shared.security.Actor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de AccountController. Se instancia directamente, sin MockMvc: el controller solo traduce HTTP
 * y pasa el Actor al servicio, que decide qué ve o hace cada rol. Los permisos por rol se prueban contra la
 * cadena de seguridad real en ApiAuthorizationTest, y la decisión por rol, en AccountServiceTest.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountController")
class AccountControllerTest {

    private static final String ACCOUNT_NUMBER = "123456789012";

    private final Actor customer = new Actor(UUID.randomUUID(), false);
    private final Actor admin = new Actor(UUID.randomUUID(), true);

    @Mock
    private AccountOpeningService openingService;
    @Mock
    private AccountQueryService queryService;
    @Mock
    private AccountLifecycleService lifecycleService;

    private AccountController controller;

    @BeforeEach
    void setUp() {
        controller = new AccountController(openingService, queryService, lifecycleService);
    }

    @Test
    @DisplayName("open - responde 201 y pasa el actor, el customerId y el tipo al servicio")
    void open_deberiaDelegarActorCustomerIdYTipo() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        CreateAccountRequest request = new CreateAccountRequest();
        request.setCustomerId(customerId);
        request.setAccountType(AccountTypeEnum.CHECKING);
        AccountResponse expected = new AccountResponse();
        when(openingService.open(admin, customerId, AccountTypeEnum.CHECKING)).thenReturn(expected);

        // Act
        ResponseEntity<AccountResponse> response = controller.open(admin, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
        verify(openingService).open(admin, customerId, AccountTypeEnum.CHECKING);
        verifyNoMoreInteractions(openingService);
    }

    @Test
    @DisplayName("open - sin cuerpo (un cliente abre la suya) pasa customerId y tipo nulos")
    void open_sinCuerpoDeberiaPasarNulos() {
        // Arrange
        AccountResponse expected = new AccountResponse();
        when(openingService.open(customer, null, null)).thenReturn(expected);

        // Act
        ResponseEntity<AccountResponse> response = controller.open(customer, null);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
        verify(openingService).open(customer, null, null);
    }

    @Test
    @DisplayName("myAccounts - lista las cuentas del usuario del actor")
    void myAccounts_deberiaDelegarElIdDelUsuario() {
        // Arrange
        List<AccountResponse> expected = List.of(new AccountResponse());
        when(queryService.getMyAccounts(customer.userId())).thenReturn(expected);

        // Act
        ResponseEntity<List<AccountResponse>> response = controller.myAccounts(customer);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(queryService).getMyAccounts(customer.userId());
        verifyNoMoreInteractions(queryService);
    }

    @Test
    @DisplayName("account - delega actor y número de cuenta, sin decidir nada por rol")
    void account_deberiaDelegarActorYNumero() {
        // Arrange
        AccountResponse forCustomer = new AccountResponse();
        AccountResponse forAdmin = new AccountResponse();
        when(queryService.getAccount(customer, ACCOUNT_NUMBER)).thenReturn(forCustomer);
        when(queryService.getAccount(admin, ACCOUNT_NUMBER)).thenReturn(forAdmin);

        // Act & Assert
        assertThat(controller.account(customer, ACCOUNT_NUMBER).getBody()).isSameAs(forCustomer);
        assertThat(controller.account(admin, ACCOUNT_NUMBER).getBody()).isSameAs(forAdmin);
        verify(queryService).getAccount(customer, ACCOUNT_NUMBER);
        verify(queryService).getAccount(admin, ACCOUNT_NUMBER);
        verifyNoMoreInteractions(queryService);
    }

    @Test
    @DisplayName("balance - delega actor y número de cuenta")
    void balance_deberiaDelegarActorYNumero() {
        // Arrange
        AccountBalanceResponse expected = new AccountBalanceResponse();
        when(queryService.getBalance(customer, ACCOUNT_NUMBER)).thenReturn(expected);

        // Act
        ResponseEntity<AccountBalanceResponse> response = controller.balance(customer, ACCOUNT_NUMBER);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(queryService).getBalance(customer, ACCOUNT_NUMBER);
        verifyNoMoreInteractions(queryService);
    }

    @Test
    @DisplayName("closeAccount - delega actor y número de cuenta")
    void closeAccount_deberiaDelegarActorYNumero() {
        // Arrange
        AccountResponse expected = new AccountResponse();
        when(lifecycleService.closeAccount(admin, ACCOUNT_NUMBER)).thenReturn(expected);

        // Act
        ResponseEntity<AccountResponse> response = controller.closeAccount(admin, ACCOUNT_NUMBER);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(lifecycleService).closeAccount(admin, ACCOUNT_NUMBER);
        verifyNoMoreInteractions(lifecycleService);
    }

    @Test
    @DisplayName("blockAccount - bloquea con el motivo y el administrador tomado del actor")
    void blockAccount_deberiaDelegarNumeroMotivoYAdministrador() {
        // Arrange
        BlockAccountRequest request = new BlockAccountRequest();
        request.setReason("Actividad sospechosa");
        AccountResponse expected = new AccountResponse();
        when(lifecycleService.blockAccount(ACCOUNT_NUMBER, "Actividad sospechosa", admin.userId())).thenReturn(expected);

        // Act
        ResponseEntity<AccountResponse> response = controller.blockAccount(admin, ACCOUNT_NUMBER, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(lifecycleService).blockAccount(ACCOUNT_NUMBER, "Actividad sospechosa", admin.userId());
        verifyNoMoreInteractions(lifecycleService);
    }

    @Test
    @DisplayName("unblockAccount - desbloquea con el motivo y el administrador tomado del actor")
    void unblockAccount_deberiaDelegarNumeroMotivoYAdministrador() {
        // Arrange
        UnblockAccountRequest request = new UnblockAccountRequest();
        request.setReason("Titular verificado");
        AccountResponse expected = new AccountResponse();
        when(lifecycleService.unblockAccount(ACCOUNT_NUMBER, "Titular verificado", admin.userId())).thenReturn(expected);

        // Act
        ResponseEntity<AccountResponse> response = controller.unblockAccount(admin, ACCOUNT_NUMBER, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(lifecycleService).unblockAccount(ACCOUNT_NUMBER, "Titular verificado", admin.userId());
        verifyNoMoreInteractions(lifecycleService);
    }

    @Test
    @DisplayName("accountsByCustomer - delega tipo y número de documento y responde 200")
    void accountsByCustomer_deberiaDelegarDocumentoYResponder200() {
        // Arrange
        List<AccountResponse> expected = List.of(new AccountResponse());
        when(queryService.getAccountsByDocument((short) 1, "1000000001")).thenReturn(expected);

        // Act
        ResponseEntity<List<AccountResponse>> response = controller.accountsByCustomer((short) 1, "1000000001");

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(queryService).getAccountsByDocument((short) 1, "1000000001");
        verifyNoMoreInteractions(queryService);
    }

    @Test
    @DisplayName("balancesByCustomer - delega tipo y número de documento y responde 200")
    void balancesByCustomer_deberiaDelegarDocumentoYResponder200() {
        // Arrange
        List<AccountBalanceResponse> expected = List.of(new AccountBalanceResponse());
        when(queryService.getBalancesByDocument((short) 1, "1000000001")).thenReturn(expected);

        // Act
        ResponseEntity<List<AccountBalanceResponse>> response = controller.balancesByCustomer((short) 1, "1000000001");

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(queryService).getBalancesByDocument((short) 1, "1000000001");
        verifyNoMoreInteractions(queryService);
    }
}
