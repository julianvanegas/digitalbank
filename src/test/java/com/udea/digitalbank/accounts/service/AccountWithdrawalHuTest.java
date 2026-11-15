package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.mapper.AccountMapper;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.shared.exception.accounts.AccountNotActiveException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.exception.accounts.InsufficientFundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.udea.digitalbank.accounts.service.AccountFixtures.buildAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link AccountApiService#applyWithdrawal}, la operación de solo débito que usará el
 * retiro de HU19. Las validaciones las hace {@code Account.debit}: aquí se prueba que la operación las aplica sobre la
 * fila bloqueada y que un rechazo no cambia el saldo. AccountLookup y el mapper son reales sobre los mocks.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU19 - applyWithdrawal - débito de un retiro")
class AccountWithdrawalHuTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerApi customerApi;

    private AccountApiService accountApiService;

    @BeforeEach
    void setUp() {
        accountApiService = new AccountApiService(accountRepository, new AccountLookup(accountRepository),
                Mappers.getMapper(AccountMapper.class), customerApi);
    }

    // La cuenta que devuelve la consulta con bloqueo; buildAccount la deja con saldo 150.75
    private Account lockedAccount(AccountStatusEnum status) {
        Account account = buildAccount(status);
        when(accountRepository.findAllByIdForUpdate(List.of(account.getId()))).thenReturn(List.of(account));
        return account;
    }

    @Test
    @DisplayName("HU19 CA01 - Debita una cuenta activa con saldo suficiente, guarda el saldo nuevo y lo devuelve")
    void deberiaDebitarYGuardar() {
        // Arrange
        Account account = lockedAccount(AccountStatusEnum.ACTIVE);
        when(accountRepository.save(account)).thenReturn(account);

        // Act
        AccountView view = accountApiService.applyWithdrawal(account.getId(), new BigDecimal("50.25"));

        // Assert
        assertThat(account.getBalance()).isEqualByComparingTo("100.50");
        assertThat(view.balance()).isEqualByComparingTo("100.50");
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("HU19 CA02 - Con saldo insuficiente lanza InsufficientFundsException y el saldo no cambia")
    void deberiaRechazarSinFondosSinCambiarElSaldo() {
        // Arrange
        Account account = lockedAccount(AccountStatusEnum.ACTIVE);
        UUID accountId = account.getId();
        BigDecimal amount = new BigDecimal("150.76");

        // Act + Assert
        assertThatThrownBy(() -> accountApiService.applyWithdrawal(accountId, amount))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("150.75");
        verify(accountRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatusEnum.class, names = {"INACTIVE", "BLOCKED", "CLOSED"})
    @DisplayName("HU19 CA01 - Una cuenta que no está ACTIVE se rechaza con AccountNotActiveException y el saldo no cambia")
    void deberiaRechazarUnaCuentaNoActiva(AccountStatusEnum status) {
        // Arrange
        Account account = lockedAccount(status);
        UUID accountId = account.getId();
        BigDecimal amount = new BigDecimal("10.00");

        // Act + Assert
        assertThatThrownBy(() -> accountApiService.applyWithdrawal(accountId, amount))
                .isInstanceOf(AccountNotActiveException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("150.75");
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU19 - Una cuenta inexistente lanza AccountNotFoundException")
    void deberiaRechazarUnaCuentaInexistente() {
        // Arrange
        UUID missing = UUID.randomUUID();
        when(accountRepository.findAllByIdForUpdate(List.of(missing))).thenReturn(List.of());
        BigDecimal amount = new BigDecimal("10.00");

        // Act + Assert
        assertThatThrownBy(() -> accountApiService.applyWithdrawal(missing, amount))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(missing.toString());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU19 - La cuenta se lee con bloqueo de escritura, nunca con una lectura normal")
    void deberiaLeerLaCuentaConBloqueo() {
        // Arrange
        Account account = lockedAccount(AccountStatusEnum.ACTIVE);
        when(accountRepository.save(account)).thenReturn(account);

        // Act
        accountApiService.applyWithdrawal(account.getId(), new BigDecimal("1.00"));

        // Assert
        verify(accountRepository).findAllByIdForUpdate(List.of(account.getId()));
        verify(accountRepository, never()).findById(any());
        verify(accountRepository, never()).findByAccountNumber(any());
    }
}
