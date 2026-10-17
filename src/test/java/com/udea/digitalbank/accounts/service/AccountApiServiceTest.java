package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.domain.AccountStatus;
import com.udea.digitalbank.accounts.mapper.AccountMapper;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.shared.exception.accounts.AccountNotActiveException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.exception.accounts.InsufficientFundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link AccountApiService}, el contrato que accounts ofrece a los demás
 * módulos: la vista que entrega y los errores con los que responde cuando la cuenta no sirve.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountApiService")
class AccountApiServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String ACCOUNT_NUMBER = "100000000001";

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerApi customerApi;

    private AccountApiService accountApiService;

    @BeforeEach
    void setUp() {
        AccountMapper accountMapper = Mappers.getMapper(AccountMapper.class);
        accountApiService = new AccountApiService(accountRepository, new AccountLookup(accountRepository),
                accountMapper, customerApi);
    }

    private static Account buildAccount(AccountStatusEnum statusEnum) {
        AccountStatus status = new AccountStatus();
        ReflectionTestUtils.setField(status, "id", (short) 1);
        ReflectionTestUtils.setField(status, "code", statusEnum.name());

        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(account, "customerId", CUSTOMER_ID);
        ReflectionTestUtils.setField(account, "accountIndex", (short) 1);
        ReflectionTestUtils.setField(account, "accountNumber", ACCOUNT_NUMBER);
        ReflectionTestUtils.setField(account, "status", status);
        ReflectionTestUtils.setField(account, "balance", new BigDecimal("150.75"));
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
    @DisplayName("getAccountByNumber - busca por el número de 12 dígitos")
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

    private static CustomerView customerView() {
        return new CustomerView(CUSTOMER_ID, UUID.randomUUID(), "Ana", "Gómez", "CC", "1000000001", "ana@example.com",
                "ACTIVE");
    }

    @Test
    @DisplayName("requireOwnedAccount - devuelve la cuenta si es del cliente dueño del usuario")
    void requireOwnedAccount_deberiaDevolverLaCuentaPropia() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Account account = buildAccount(AccountStatusEnum.BLOCKED);
        when(customerApi.getCustomerByUserId(userId)).thenReturn(customerView());
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        // Act
        AccountView view = accountApiService.requireOwnedAccount(userId, ACCOUNT_NUMBER);

        // Assert: en cualquier estado, quien decide si sirve es el llamante
        assertThat(view.id()).isEqualTo(account.getId());
        assertThat(view.status()).isEqualTo(AccountStatusEnum.BLOCKED.name());
    }

    @Test
    @DisplayName("requireOwnedAccount - una cuenta de otro cliente responde igual que una inexistente")
    void requireOwnedAccount_deberiaOcultarLaCuentaAjena() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Account foreign = buildAccount(AccountStatusEnum.ACTIVE);
        ReflectionTestUtils.setField(foreign, "customerId", UUID.randomUUID());
        when(customerApi.getCustomerByUserId(userId)).thenReturn(customerView());
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(foreign));

        // Act + Assert
        assertThatThrownBy(() -> accountApiService.requireOwnedAccount(userId, ACCOUNT_NUMBER))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Cuenta no encontrada: " + ACCOUNT_NUMBER);
    }

    @Nested
    @DisplayName("applyTransfer")
    class ApplyTransfer {

        private final Account sender = buildAccount(AccountStatusEnum.ACTIVE);
        private final Account receiver = buildAccount(AccountStatusEnum.ACTIVE);

        private void lock(Account... accounts) {
            when(accountRepository.findAllByIdForUpdate(List.of(sender.getId(), receiver.getId())))
                    .thenReturn(List.of(accounts));
        }

        @Test
        @DisplayName("Bloquea las dos cuentas, debita a la emisora y acredita a la receptora")
        void deberiaMoverElDinero() {
            // Arrange
            lock(sender, receiver);

            // Act
            accountApiService.applyTransfer(sender.getId(), receiver.getId(), new BigDecimal("50.25"));

            // Assert
            assertThat(sender.getBalance()).isEqualByComparingTo("100.50");
            assertThat(receiver.getBalance()).isEqualByComparingTo("201.00");
            verify(accountRepository).saveAll(List.of(sender, receiver));
        }

        @Test
        @DisplayName("Con saldo insuficiente lanza InsufficientFundsException y no cambia ninguna cuenta")
        void deberiaRechazarSinMoverNada() {
            // Arrange
            lock(sender, receiver);

            // Act + Assert
            assertThatThrownBy(() -> accountApiService.applyTransfer(sender.getId(), receiver.getId(),
                    new BigDecimal("150.76")))
                    .isInstanceOf(InsufficientFundsException.class);
            assertThat(sender.getBalance()).isEqualByComparingTo("150.75");
            assertThat(receiver.getBalance()).isEqualByComparingTo("150.75");
            verify(accountRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Si la receptora no está activa, la emisora tampoco se debita")
        void noDeberiaDejarLaTransferenciaAMedias() {
            // Arrange
            Account inactive = buildAccount(AccountStatusEnum.INACTIVE);
            when(accountRepository.findAllByIdForUpdate(List.of(sender.getId(), inactive.getId())))
                    .thenReturn(List.of(sender, inactive));

            // Act + Assert
            assertThatThrownBy(() -> accountApiService.applyTransfer(sender.getId(), inactive.getId(), BigDecimal.TEN))
                    .isInstanceOf(AccountNotActiveException.class);
            assertThat(sender.getBalance()).isEqualByComparingTo("150.75");
            assertThat(inactive.getBalance()).isEqualByComparingTo("150.75");
        }

        @Test
        @DisplayName("Si una de las cuentas no existe lanza AccountNotFoundException")
        void deberiaFallarSiFaltaUnaCuenta() {
            // Arrange
            lock(sender);

            // Act + Assert
            assertThatThrownBy(() -> accountApiService.applyTransfer(sender.getId(), receiver.getId(), BigDecimal.TEN))
                    .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("Una transferencia de una cuenta a sí misma es un error de programación")
        void deberiaExigirDosCuentasDistintas() {
            assertThatThrownBy(() -> accountApiService.applyTransfer(sender.getId(), sender.getId(), BigDecimal.TEN))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(accountRepository);
        }
    }
}
