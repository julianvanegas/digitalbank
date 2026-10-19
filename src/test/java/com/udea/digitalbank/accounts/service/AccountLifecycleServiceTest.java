package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.domain.AccountAction;
import com.udea.digitalbank.accounts.domain.AccountActionType;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.mapper.AccountMapper;
import com.udea.digitalbank.accounts.repository.AccountActionRepository;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.shared.exception.InvalidRequestException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotBlockableException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotClosableException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotUnblockableException;
import com.udea.digitalbank.shared.security.Actor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static com.udea.digitalbank.accounts.service.AccountFixtures.ACCOUNT_NUMBER;
import static com.udea.digitalbank.accounts.service.AccountFixtures.buildAccount;
import static com.udea.digitalbank.accounts.service.AccountFixtures.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link AccountLifecycleService}: cerrar, bloquear y desbloquear. Las dependencias se
 * mockean; AccountLookup y el mapper son reales sobre los mismos mocks.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountLifecycleService")
class AccountLifecycleServiceTest {

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

    @BeforeEach
    void setUp() {
        lifecycleService = new AccountLifecycleService(accountRepository, accountActionRepository, customerApi,
                Mappers.getMapper(AccountMapper.class), new AccountLookup(accountRepository), accountCatalog,
                eventPublisher);
    }

    @Nested
    @DisplayName("cierre de cuenta")
    class CloseAccount {

        private final UUID userId = UUID.randomUUID();
        private final UUID customerId = UUID.randomUUID();
        private final UUID adminId = UUID.randomUUID();

        private Account accountOf(AccountStatusEnum statusEnum, String balance, UUID owner) {
            Account account = buildAccount(statusEnum);
            ReflectionTestUtils.setField(account, "customerId", owner);
            ReflectionTestUtils.setField(account, "balance", new BigDecimal(balance));
            return account;
        }

        private void stubCustomer() {
            CustomerView customer = new CustomerView(customerId, userId, "Ana", "Gómez",
                    "Cédula de ciudadanía", "1000000001", "ana@example.com", "ACTIVE");
            when(customerApi.getCustomerByUserId(userId)).thenReturn(customer);
        }

        private void stubClosing(Account account) {
            when(accountRepository.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));
            when(accountCatalog.status(AccountStatusEnum.CLOSED)).thenReturn(status(AccountStatusEnum.CLOSED));
            when(accountRepository.save(account)).thenReturn(account);
        }

        private void assertActionLogged(Account account, String reason, UUID by) {
            ArgumentCaptor<AccountAction> captor = ArgumentCaptor.forClass(AccountAction.class);
            verify(accountActionRepository).save(captor.capture());
            AccountAction logged = captor.getValue();
            assertThat(logged.getAction()).isEqualTo(AccountActionType.CLOSED);
            assertThat(logged.getAccountId()).isEqualTo(account.getId());
            assertThat(logged.getReason()).isEqualTo(reason);
            assertThat(logged.getCreatedBy()).isEqualTo(by);
        }

        private void assertNotified(Account account, String reason) {
            ArgumentCaptor<AccountActionEvent> captor = ArgumentCaptor.forClass(AccountActionEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            AccountActionEvent event = captor.getValue();
            assertThat(event.action()).isEqualTo(AccountActionType.CLOSED);
            assertThat(event.customerId()).isEqualTo(account.getCustomerId());
            assertThat(event.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
            assertThat(event.reason()).isEqualTo(reason);
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatusEnum.class, names = {"ACTIVE", "INACTIVE"})
        @DisplayName("El titular cierra una cuenta sin saldo: queda CLOSED con fecha de cierre")
        void deberiaCerrarLaCuentaDelTitular(AccountStatusEnum current) {
            // Arrange
            stubCustomer();
            Account account = accountOf(current, "0.00", customerId);
            stubClosing(account);

            // Act
            AccountResponse response = lifecycleService.closeAccount(new Actor(userId, false), ACCOUNT_NUMBER);

            // Assert
            assertThat(account.getStatus().is(AccountStatusEnum.CLOSED)).isTrue();
            assertThat(account.getClosedAt()).isNotNull();
            assertThat(response.getStatus()).isEqualTo("CLOSED");
            assertThat(response.getClosedAt()).isEqualTo(account.getClosedAt());
            verify(accountRepository).save(account);
            assertActionLogged(account, AccountLifecycleService.CLOSED_BY_CUSTOMER, userId);
            assertNotified(account, AccountLifecycleService.CLOSED_BY_CUSTOMER);
        }

        @Test
        @DisplayName("Un administrador cierra la cuenta de cualquier cliente sin pasar por customerApi")
        void deberiaCerrarCualquierCuentaComoAdministrador() {
            // Arrange
            Account account = accountOf(AccountStatusEnum.ACTIVE, "0", UUID.randomUUID());
            stubClosing(account);

            // Act
            AccountResponse response = lifecycleService.closeAccount(new Actor(adminId, true), ACCOUNT_NUMBER);

            // Assert
            assertThat(response.getStatus()).isEqualTo("CLOSED");
            assertThat(account.getClosedAt()).isNotNull();
            verifyNoInteractions(customerApi);
            assertActionLogged(account, AccountLifecycleService.CLOSED_BY_ADMIN, adminId);
            assertNotified(account, AccountLifecycleService.CLOSED_BY_ADMIN);
        }

        @Test
        @DisplayName("Una cuenta ajena responde como inexistente y no se cierra")
        void deberiaTratarUnaCuentaAjenaComoInexistente() {
            // Arrange
            stubCustomer();
            Account foreign = accountOf(AccountStatusEnum.ACTIVE, "0.00", UUID.randomUUID());
            when(accountRepository.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.of(foreign));

            // Act & Assert
            assertThatThrownBy(() -> lifecycleService.closeAccount(new Actor(userId, false), ACCOUNT_NUMBER))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining(ACCOUNT_NUMBER);
            assertThat(foreign.getStatus().is(AccountStatusEnum.ACTIVE)).isTrue();
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("Lanza AccountNotFoundException cuando el número no existe")
        void deberiaLanzarExcepcionCuandoLaCuentaNoExiste() {
            // Arrange
            when(accountRepository.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> lifecycleService.closeAccount(new Actor(adminId, true), ACCOUNT_NUMBER))
                    .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("Rechaza cerrar una cuenta con saldo, aunque lo pida un administrador")
        void deberiaRechazarUnaCuentaConSaldo() {
            // Arrange
            Account account = accountOf(AccountStatusEnum.ACTIVE, "0.01", UUID.randomUUID());
            when(accountRepository.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

            // Act & Assert
            assertThatThrownBy(() -> lifecycleService.closeAccount(new Actor(adminId, true), ACCOUNT_NUMBER))
                    .isInstanceOf(AccountNotClosableException.class)
                    .hasMessageContaining("saldo");
            assertThat(account.getStatus().is(AccountStatusEnum.ACTIVE)).isTrue();
            assertThat(account.getClosedAt()).isNull();
            verify(accountRepository, never()).save(any());
            verifyNoInteractions(accountActionRepository, eventPublisher);
        }

        @Test
        @DisplayName("Rechaza cerrar una cuenta bloqueada: primero se debe desbloquearla")
        void deberiaRechazarUnaCuentaBloqueada() {
            // Arrange
            Account account = accountOf(AccountStatusEnum.BLOCKED, "0.00", UUID.randomUUID());
            when(accountRepository.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

            // Act & Assert
            assertThatThrownBy(() -> lifecycleService.closeAccount(new Actor(adminId, true), ACCOUNT_NUMBER))
                    .isInstanceOf(AccountNotClosableException.class)
                    .hasMessageContaining("bloqueada");
            assertThat(account.getStatus().is(AccountStatusEnum.BLOCKED)).isTrue();
            verify(accountRepository, never()).save(any());
            verifyNoInteractions(accountActionRepository, eventPublisher);
        }

        @Test
        @DisplayName("Rechaza cerrar una cuenta que ya está cerrada")
        void deberiaRechazarUnaCuentaYaCerrada() {
            // Arrange
            Account account = accountOf(AccountStatusEnum.CLOSED, "0.00", UUID.randomUUID());
            when(accountRepository.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

            // Act & Assert
            assertThatThrownBy(() -> lifecycleService.closeAccount(new Actor(adminId, true), ACCOUNT_NUMBER))
                    .isInstanceOf(AccountNotClosableException.class)
                    .hasMessageContaining("ya está cerrada");
            verify(accountRepository, never()).save(any());
            verifyNoInteractions(accountActionRepository, eventPublisher);
        }
    }

    @Nested
    @DisplayName("blockAccount - bloqueo de una cuenta")
    class BlockAccount {

        @Test
        @DisplayName("Bloquea una cuenta activa y registra la acción BLOCKED con motivo y administrador")
        void deberiaBloquearLaCuentaYRegistrarLaAccion() {
            // Arrange
            UUID adminId = UUID.randomUUID();
            Account account = buildAccount(AccountStatusEnum.ACTIVE);
            when(accountRepository.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));
            when(accountCatalog.status(AccountStatusEnum.BLOCKED)).thenReturn(status(AccountStatusEnum.BLOCKED));
            when(accountActionRepository.save(any(AccountAction.class))).thenAnswer(i -> i.getArgument(0));
            when(accountRepository.save(account)).thenReturn(account);

            // Act
            AccountResponse response = lifecycleService.blockAccount(ACCOUNT_NUMBER, "  Actividad sospechosa  ", adminId);

            // Assert
            assertThat(account.getStatus().is(AccountStatusEnum.BLOCKED)).isTrue();
            assertThat(response.getStatus()).isEqualTo("BLOCKED");
            ArgumentCaptor<AccountAction> captor = ArgumentCaptor.forClass(AccountAction.class);
            verify(accountActionRepository).save(captor.capture());
            AccountAction logged = captor.getValue();
            assertThat(logged.getAction()).isEqualTo(AccountActionType.BLOCKED);
            assertThat(logged.getAccountId()).isEqualTo(account.getId());
            assertThat(logged.getReason()).isEqualTo("Actividad sospechosa");
            assertThat(logged.getCreatedBy()).isEqualTo(adminId);
            ArgumentCaptor<AccountActionEvent> eventCaptor = ArgumentCaptor.forClass(AccountActionEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            AccountActionEvent event = eventCaptor.getValue();
            assertThat(event.action()).isEqualTo(AccountActionType.BLOCKED);
            assertThat(event.customerId()).isEqualTo(account.getCustomerId());
            assertThat(event.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
            assertThat(event.reason()).isEqualTo("Actividad sospechosa");
            verify(accountRepository, never()).findByAccountNumber(any());
        }

        @Test
        @DisplayName("Rechaza bloquear una cuenta que ya está bloqueada")
        void deberiaRechazarUnaCuentaYaBloqueada() {
            // Arrange
            Account account = buildAccount(AccountStatusEnum.BLOCKED);
            when(accountRepository.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

            // Act & Assert
            assertThatThrownBy(() -> lifecycleService.blockAccount(ACCOUNT_NUMBER, "Motivo", UUID.randomUUID()))
                    .isInstanceOf(AccountNotBlockableException.class);
            verifyNoInteractions(accountActionRepository);
        }

        @Test
        @DisplayName("Rechaza un motivo vacío")
        void deberiaRechazarUnMotivoVacio() {
            // Act & Assert
            assertThatThrownBy(() -> lifecycleService.blockAccount(ACCOUNT_NUMBER, "   ", UUID.randomUUID()))
                    .isInstanceOf(InvalidRequestException.class);
            verifyNoInteractions(accountActionRepository);
        }
    }

    @Nested
    @DisplayName("unblockAccount - desbloqueo de una cuenta")
    class UnblockAccount {

        @Test
        @DisplayName("Devuelve a ACTIVE una cuenta bloqueada, conserva su saldo y registra la acción UNBLOCKED con su motivo")
        void deberiaActivarLaCuentaYRegistrarElDesbloqueo() {
            // Arrange
            UUID adminId = UUID.randomUUID();
            Account account = buildAccount(AccountStatusEnum.BLOCKED);
            when(accountRepository.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));
            when(accountCatalog.status(AccountStatusEnum.ACTIVE)).thenReturn(status(AccountStatusEnum.ACTIVE));
            when(accountRepository.save(account)).thenReturn(account);

            // Act
            AccountResponse response = lifecycleService.unblockAccount(ACCOUNT_NUMBER, "  Titular verificado  ", adminId);

            // Assert
            assertThat(account.getStatus().is(AccountStatusEnum.ACTIVE)).isTrue();
            assertThat(account.getBalance()).isEqualByComparingTo("150.75");
            assertThat(response.getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);
            ArgumentCaptor<AccountAction> captor = ArgumentCaptor.forClass(AccountAction.class);
            verify(accountActionRepository).save(captor.capture());
            AccountAction logged = captor.getValue();
            assertThat(logged.getAction()).isEqualTo(AccountActionType.UNBLOCKED);
            assertThat(logged.getAccountId()).isEqualTo(account.getId());
            assertThat(logged.getCreatedBy()).isEqualTo(adminId);
            assertThat(logged.getReason()).isEqualTo("Titular verificado");
            ArgumentCaptor<AccountActionEvent> eventCaptor = ArgumentCaptor.forClass(AccountActionEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            AccountActionEvent event = eventCaptor.getValue();
            assertThat(event.action()).isEqualTo(AccountActionType.UNBLOCKED);
            assertThat(event.customerId()).isEqualTo(account.getCustomerId());
            assertThat(event.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
            assertThat(event.reason()).isEqualTo("Titular verificado");
            verify(accountRepository).save(account);
            verify(accountRepository, never()).findByAccountNumber(any());
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatusEnum.class, names = "BLOCKED", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Rechaza con conflicto cualquier cuenta que no esté bloqueada")
        void deberiaRechazarUnaCuentaQueNoEstaBloqueada(AccountStatusEnum current) {
            // Arrange
            Account account = buildAccount(current);
            when(accountRepository.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

            // Act & Assert
            assertThatThrownBy(() -> lifecycleService.unblockAccount(ACCOUNT_NUMBER, "Titular verificado", UUID.randomUUID()))
                    .isInstanceOf(AccountNotUnblockableException.class)
                    .hasMessageContaining(current.name());
            assertThat(account.getStatus().is(current)).isTrue();
            verify(accountRepository, never()).save(any());
            verifyNoInteractions(accountActionRepository, eventPublisher);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("Rechaza un motivo vacío, en blanco o ausente sin tocar la cuenta")
        void deberiaRechazarUnMotivoVacio(String reason) {
            // Act & Assert
            assertThatThrownBy(() -> lifecycleService.unblockAccount(ACCOUNT_NUMBER, reason, UUID.randomUUID()))
                    .isInstanceOf(InvalidRequestException.class);
            verifyNoInteractions(accountRepository, accountActionRepository);
        }

        @Test
        @DisplayName("Lanza AccountNotFoundException cuando el número no existe")
        void deberiaLanzarExcepcionCuandoLaCuentaNoExiste() {
            // Arrange
            when(accountRepository.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> lifecycleService.unblockAccount(ACCOUNT_NUMBER, "Titular verificado", UUID.randomUUID()))
                    .isInstanceOf(AccountNotFoundException.class);
            verify(accountRepository, never()).save(any());
        }
    }
}
