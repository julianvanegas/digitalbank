package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.domain.AccountStatus;
import com.udea.digitalbank.accounts.mapper.AccountMapper;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.shared.exception.accounts.AccountNotActiveException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link AccountApiService}, el contrato que accounts ofrece a los demás
 * módulos: la vista que entrega y los errores con los que responde cuando la cuenta no sirve.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountApiService")
class AccountApiServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String ACCOUNT_NUMBER = "10000000001";

    @Mock
    private AccountRepository accountRepository;

    private AccountApiService accountApiService;

    @BeforeEach
    void setUp() {
        AccountMapper accountMapper = Mappers.getMapper(AccountMapper.class);
        accountApiService = new AccountApiService(accountRepository, new AccountLookup(accountRepository),
                accountMapper);
    }

    private static Account buildAccount(AccountStatusEnum statusEnum) {
        AccountStatus status = new AccountStatus();
        ReflectionTestUtils.setField(status, "id", (short) 1);
        ReflectionTestUtils.setField(status, "code", statusEnum.name());

        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        account.setCustomerId(CUSTOMER_ID);
        account.setAccountIndex((short) 1);
        account.setAccountNumber(ACCOUNT_NUMBER);
        account.setStatus(status);
        account.setBalance(new BigDecimal("150.75"));
        return account;
    }

    @Test
    @DisplayName("getAccount - entrega la vista de solo lectura de la cuenta")
    void getAccount_deberiaDevolverLaVista() {
        // Arrange
        Account account = buildAccount(AccountStatusEnum.ACTIVE);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        // Act
        AccountView view = accountApiService.getAccount(account.getId());

        // Assert
        assertThat(view.id()).isEqualTo(account.getId());
        assertThat(view.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
        assertThat(view.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(view.status()).isEqualTo(AccountStatusEnum.ACTIVE.name());
        assertThat(view.balance()).isEqualByComparingTo("150.75");
    }

    @Test
    @DisplayName("getAccount - lanza AccountNotFoundException si la cuenta no existe")
    void getAccount_deberiaFallarSiNoExiste() {
        // Arrange
        UUID missing = UUID.randomUUID();
        when(accountRepository.findById(missing)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> accountApiService.getAccount(missing))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    @DisplayName("getAccountByNumber - busca por el número de 11 dígitos")
    void getAccountByNumber_deberiaDevolverLaVista() {
        // Arrange
        Account account = buildAccount(AccountStatusEnum.ACTIVE);
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        // Act
        AccountView view = accountApiService.getAccountByNumber(ACCOUNT_NUMBER);

        // Assert
        assertThat(view.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
    }

    @Test
    @DisplayName("getAccountByNumber - lanza AccountNotFoundException si el número no existe")
    void getAccountByNumber_deberiaFallarSiNoExiste() {
        // Arrange
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> accountApiService.getAccountByNumber(ACCOUNT_NUMBER))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("getAccountsOfCustomer - devuelve las cuentas del cliente")
    void getAccountsOfCustomer_deberiaDevolverLasCuentas() {
        // Arrange
        when(accountRepository.findByCustomerIdOrderByCreatedAt(CUSTOMER_ID))
                .thenReturn(List.of(buildAccount(AccountStatusEnum.ACTIVE)));

        // Act
        List<AccountView> views = accountApiService.getAccountsOfCustomer(CUSTOMER_ID);

        // Assert
        assertThat(views).hasSize(1);
        assertThat(views.getFirst().customerId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    @DisplayName("getAccountsOfCustomer - devuelve lista vacía si el cliente no tiene cuentas")
    void getAccountsOfCustomer_deberiaDevolverListaVacia() {
        // Arrange
        when(accountRepository.findByCustomerIdOrderByCreatedAt(CUSTOMER_ID)).thenReturn(List.of());

        // Act + Assert
        assertThat(accountApiService.getAccountsOfCustomer(CUSTOMER_ID)).isEmpty();
    }

    @Test
    @DisplayName("requireActiveAccount - devuelve la cuenta cuando está activa")
    void requireActiveAccount_deberiaDevolverLaCuentaActiva() {
        // Arrange
        Account account = buildAccount(AccountStatusEnum.ACTIVE);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        // Act
        AccountView view = accountApiService.requireActiveAccount(account.getId());

        // Assert
        assertThat(view.status()).isEqualTo(AccountStatusEnum.ACTIVE.name());
    }

    @Test
    @DisplayName("requireActiveAccount - lanza AccountNotActiveException si la cuenta está cerrada")
    void requireActiveAccount_deberiaFallarSiEstaCerrada() {
        // Arrange
        Account account = buildAccount(AccountStatusEnum.CLOSED);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        // Act + Assert
        assertThatThrownBy(() -> accountApiService.requireActiveAccount(account.getId()))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("no está activa");
    }

    @Test
    @DisplayName("requireActiveAccount - lanza AccountNotActiveException si la cuenta está inactiva")
    void requireActiveAccount_deberiaFallarSiEstaInactiva() {
        // Arrange
        Account account = buildAccount(AccountStatusEnum.INACTIVE);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        // Act + Assert
        assertThatThrownBy(() -> accountApiService.requireActiveAccount(account.getId()))
                .isInstanceOf(AccountNotActiveException.class);
    }
}
