package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountTypeEnum;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.mapper.AccountMapper;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.shared.exception.InvalidRequestException;
import com.udea.digitalbank.shared.exception.accounts.AccountLimitReachedException;
import com.udea.digitalbank.shared.exception.accounts.AccountOwnershipException;
import com.udea.digitalbank.shared.security.Actor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static com.udea.digitalbank.accounts.service.AccountFixtures.ACCOUNT_NUMBER;
import static com.udea.digitalbank.accounts.service.AccountFixtures.buildAccount;
import static com.udea.digitalbank.accounts.service.AccountFixtures.status;
import static com.udea.digitalbank.accounts.service.AccountFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link AccountOpeningService}: quién abre, tipo de cuenta y cupo de tres abiertas.
 * Las dependencias se mockean; el mapper es real.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountOpeningService")
class AccountOpeningServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerApi customerApi;
    @Mock
    private AccountNumberGenerator accountNumberGenerator;
    @Mock
    private AccountKeyGenerator accountKeyGenerator;
    @Mock
    private AccountCatalog accountCatalog;

    private AccountOpeningService openingService;

    @BeforeEach
    void setUp() {
        openingService = new AccountOpeningService(accountRepository, customerApi,
                Mappers.getMapper(AccountMapper.class), accountNumberGenerator, accountKeyGenerator, accountCatalog);
    }

    @Nested
    @DisplayName("quién puede abrir una cuenta")
    class WhoCanOpen {

        private final UUID customerUserId = UUID.randomUUID();
        private final UUID customerId = UUID.randomUUID();
        private final Actor customerActor = new Actor(customerUserId, false);
        private final Actor adminActor = new Actor(UUID.randomUUID(), true);

        @Test
        @DisplayName("Un administrador sin customerId no puede abrir una cuenta")
        void deberiaExigirElCustomerIdAlAdministrador() {
            assertThatThrownBy(() -> openingService.open(adminActor, null, null))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("customerId");
            verifyNoInteractions(customerApi, accountRepository);
        }

        @Test
        @DisplayName("Un cliente no puede abrir una cuenta a nombre de otro cliente")
        void deberiaRechazarQueUnClienteAbraLaCuentaDeOtro() {
            CustomerView customer = new CustomerView(customerId, customerUserId, "Ana", "Gómez",
                    "Cédula de ciudadanía", "1000000001", "ana@example.com", "ACTIVE");
            when(customerApi.getCustomerByUserId(customerUserId)).thenReturn(customer);

            assertThatThrownBy(() -> openingService.open(customerActor, UUID.randomUUID(), null))
                    .isInstanceOf(AccountOwnershipException.class);
            verify(accountRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("apertura - tipo de cuenta")
    class Open {

        private final UUID customerId = UUID.randomUUID();

        private void stubOpening() {
            CustomerView customer = new CustomerView(customerId, UUID.randomUUID(), "Ana", "Gómez",
                    "Cédula de ciudadanía", "1000000001", "ana@example.com", "ACTIVE");
            when(customerApi.requireActiveCustomer(customerId)).thenReturn(customer);
            when(accountRepository.findByCustomerIdOrderByCreatedAt(customerId)).thenReturn(java.util.List.of());
            when(accountNumberGenerator.next()).thenReturn(ACCOUNT_NUMBER);
            when(accountKeyGenerator.build("Ana", "1000000001")).thenReturn("@AN0011");
            when(accountCatalog.status(AccountStatusEnum.ACTIVE)).thenReturn(status(AccountStatusEnum.ACTIVE));
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        @DisplayName("Sin tipo indicado abre una cuenta SAVINGS")
        void deberiaAbrirCuentaDeAhorrosPorDefecto() {
            // Arrange
            stubOpening();
            when(accountCatalog.type(AccountTypeEnum.SAVINGS)).thenReturn(type(AccountTypeEnum.SAVINGS));

            // Act
            AccountResponse response = openingService.open(new Actor(UUID.randomUUID(), true), customerId, null);

            // Assert
            ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(captor.capture());
            assertThat(captor.getValue().getAccountType().getCode()).isEqualTo("SAVINGS");
            assertThat(response.getAccountType()).isEqualTo("SAVINGS");
            assertThat(response.getAccountNumber()).hasSize(12);
        }

        @Test
        @DisplayName("Abre la cuenta con el tipo indicado")
        void deberiaAbrirCuentaDelTipoIndicado() {
            // Arrange
            stubOpening();
            when(accountCatalog.type(AccountTypeEnum.CHECKING)).thenReturn(type(AccountTypeEnum.CHECKING));

            // Act
            AccountResponse response = openingService.open(new Actor(UUID.randomUUID(), true), customerId, AccountTypeEnum.CHECKING);

            // Assert
            assertThat(response.getAccountType()).isEqualTo("CHECKING");
            verify(accountCatalog, never()).type(AccountTypeEnum.SAVINGS);
        }
    }

    @Nested
    @DisplayName("apertura - límite de tres cuentas abiertas")
    class OpenLimit {

        private final UUID customerId = UUID.randomUUID();

        private Account existing(AccountStatusEnum statusEnum, int index) {
            Account account = buildAccount(statusEnum);
            ReflectionTestUtils.setField(account, "customerId", customerId);
            ReflectionTestUtils.setField(account, "accountIndex", (short) index);
            return account;
        }

        private void stubOpening(List<Account> existing) {
            CustomerView customer = new CustomerView(customerId, UUID.randomUUID(), "Ana", "Gómez",
                    "Cédula de ciudadanía", "1000000001", "ana@example.com", "ACTIVE");
            when(customerApi.requireActiveCustomer(customerId)).thenReturn(customer);
            when(accountRepository.findByCustomerIdOrderByCreatedAt(customerId)).thenReturn(existing);
        }

        private void stubSaving() {
            when(accountNumberGenerator.next()).thenReturn("200000000001");
            when(accountKeyGenerator.build("Ana", "1000000001")).thenReturn("@AN0011");
            when(accountCatalog.type(AccountTypeEnum.SAVINGS)).thenReturn(type(AccountTypeEnum.SAVINGS));
            when(accountCatalog.status(AccountStatusEnum.ACTIVE)).thenReturn(status(AccountStatusEnum.ACTIVE));
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        private Short openedIndex() {
            ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(captor.capture());
            return captor.getValue().getAccountIndex();
        }

        @Test
        @DisplayName("Rechaza abrir una cuarta cuenta cuando las tres están abiertas")
        void deberiaRechazarLaCuartaCuentaAbierta() {
            // Arrange
            stubOpening(List.of(existing(AccountStatusEnum.ACTIVE, 1), existing(AccountStatusEnum.INACTIVE, 2),
                    existing(AccountStatusEnum.BLOCKED, 3)));

            // Act & Assert
            assertThatThrownBy(() -> openingService.open(new Actor(UUID.randomUUID(), true), customerId, null))
                    .isInstanceOf(AccountLimitReachedException.class)
                    .hasMessageContaining("abiertas");
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("Una cuenta cerrada libera su cupo: la nueva ocupa la posición que dejó libre")
        void deberiaReutilizarLaPosicionDeLaCuentaCerrada() {
            // Arrange: tres cuentas, la 2 cerrada
            stubOpening(List.of(existing(AccountStatusEnum.ACTIVE, 1), existing(AccountStatusEnum.CLOSED, 2),
                    existing(AccountStatusEnum.ACTIVE, 3)));
            stubSaving();

            // Act
            openingService.open(new Actor(UUID.randomUUID(), true), customerId, null);

            // Assert
            assertThat(openedIndex()).isEqualTo((short) 2);
        }

        @Test
        @DisplayName("Varias cuentas cerradas no cuentan: con una sola abierta se pueden abrir más")
        void noDeberiaContarLasCuentasCerradas() {
            // Arrange: la 1 abierta y las otras dos cerradas
            stubOpening(List.of(existing(AccountStatusEnum.ACTIVE, 1), existing(AccountStatusEnum.CLOSED, 2),
                    existing(AccountStatusEnum.CLOSED, 3)));
            stubSaving();

            // Act
            openingService.open(new Actor(UUID.randomUUID(), true), customerId, null);

            // Assert
            assertThat(openedIndex()).isEqualTo((short) 2);
        }

        @Test
        @DisplayName("Sin cuentas previas la primera ocupa la posición 1")
        void deberiaEmpezarEnLaPosicionUno() {
            // Arrange
            stubOpening(List.of());
            stubSaving();

            // Act
            openingService.open(new Actor(UUID.randomUUID(), true), customerId, null);

            // Assert
            assertThat(openedIndex()).isEqualTo((short) 1);
        }
    }
}
