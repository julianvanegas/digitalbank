package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.dto.AccountBalanceResponse;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.mapper.AccountMapper;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.exception.customer.CustomerNotFoundException;
import com.udea.digitalbank.shared.security.Actor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.udea.digitalbank.accounts.service.AccountFixtures.ACCOUNT_NUMBER;
import static com.udea.digitalbank.accounts.service.AccountFixtures.buildAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link AccountQueryService}: qué ve cada rol y las consultas por documento.
 * AccountLookup y el mapper son reales sobre los mismos mocks.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountQueryService")
class AccountQueryServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerApi customerApi;

    private AccountQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new AccountQueryService(accountRepository, customerApi,
                Mappers.getMapper(AccountMapper.class), new AccountLookup(accountRepository));
    }

    @Nested
    @DisplayName("acceso según el rol del actor: detalle y saldo")
    class AccessByRole {

        private final UUID customerUserId = UUID.randomUUID();
        private final UUID customerId = UUID.randomUUID();
        private final Actor customerActor = new Actor(customerUserId, false);
        private final Actor adminActor = new Actor(UUID.randomUUID(), true);

        private void stubCustomer() {
            CustomerView customer = new CustomerView(customerId, customerUserId, "Ana", "Gómez",
                    "Cédula de ciudadanía", "1000000001", "ana@example.com", "ACTIVE");
            when(customerApi.getCustomerByUserId(customerUserId)).thenReturn(customer);
        }

        private Account account(UUID owner) {
            Account account = buildAccount(AccountStatusEnum.ACTIVE);
            ReflectionTestUtils.setField(account, "customerId", owner);
            return account;
        }

        @Test
        @DisplayName("Un cliente ve el detalle y el saldo de su cuenta")
        void deberiaDejarAlClienteVerSuCuenta() {
            stubCustomer();
            Account own = account(customerId);
            when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(own));

            assertThat(queryService.getAccount(customerActor, ACCOUNT_NUMBER).getAccountNumber())
                    .isEqualTo(ACCOUNT_NUMBER);
            assertThat(queryService.getBalance(customerActor, ACCOUNT_NUMBER).getBalance())
                    .isEqualByComparingTo("150.75");
        }

        @Test
        @DisplayName("Una cuenta ajena responde como inexistente, en el detalle y en el saldo")
        void deberiaTratarComoInexistenteLaCuentaDeOtroCliente() {
            stubCustomer();
            when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account(UUID.randomUUID())));

            assertThatThrownBy(() -> queryService.getAccount(customerActor, ACCOUNT_NUMBER))
                    .isInstanceOf(AccountNotFoundException.class);
            assertThatThrownBy(() -> queryService.getBalance(customerActor, ACCOUNT_NUMBER))
                    .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("Un administrador ve cualquier cuenta sin pasar por customerApi")
        void deberiaDejarAlAdministradorVerCualquierCuenta() {
            when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account(UUID.randomUUID())));

            assertThat(queryService.getAccount(adminActor, ACCOUNT_NUMBER).getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);
            assertThat(queryService.getBalance(adminActor, ACCOUNT_NUMBER).getBalance()).isEqualByComparingTo("150.75");
            verifyNoInteractions(customerApi);
        }
    }

    @Nested
    @DisplayName("consultas de las cuentas de un cliente")
    class ByCustomer {

        private final UUID customerId = UUID.randomUUID();

        private void stubDocument() {
            CustomerView customer = new CustomerView(customerId, UUID.randomUUID(), "Ana", "Gómez",
                    "Cédula de ciudadanía", "1000000001", "ana@example.com", "ACTIVE");
            when(customerApi.getCustomerByDocument((short) 1, "1000000001")).thenReturn(customer);
        }

        @Test
        @DisplayName("Las cuentas del cliente del titular, en orden de apertura y en cualquier estado")
        void deberiaListarLasCuentasPorDocumento() {
            stubDocument();
            Account active = buildAccount(AccountStatusEnum.ACTIVE);
            Account closed = buildAccount(AccountStatusEnum.CLOSED);
            when(accountRepository.findByCustomerIdOrderByCreatedAt(customerId)).thenReturn(List.of(active, closed));

            assertThat(queryService.getAccountsByDocument((short) 1, "1000000001"))
                    .extracting(AccountResponse::getStatus).containsExactly("ACTIVE", "CLOSED");
            assertThat(queryService.getBalancesByDocument((short) 1, "1000000001"))
                    .extracting(AccountBalanceResponse::getStatus).containsExactly("ACTIVE", "CLOSED");
        }

        @Test
        @DisplayName("Un cliente sin cuentas devuelve una lista vacía, no un error")
        void deberiaDevolverVacioSinCuentas() {
            stubDocument();
            when(accountRepository.findByCustomerIdOrderByCreatedAt(customerId)).thenReturn(List.of());

            assertThat(queryService.getAccountsByDocument((short) 1, "1000000001")).isEmpty();
        }

        @Test
        @DisplayName("Un documento sin cliente propaga CustomerNotFoundException")
        void deberiaPropagarCustomerNotFound() {
            when(customerApi.getCustomerByDocument((short) 1, "000")).thenThrow(new CustomerNotFoundException("no"));

            assertThatThrownBy(() -> queryService.getAccountsByDocument((short) 1, "000"))
                    .isInstanceOf(CustomerNotFoundException.class);
            verifyNoInteractions(accountRepository);
        }

        @Test
        @DisplayName("Las cuentas del propio cliente autenticado, por su id de usuario")
        void deberiaListarMisCuentas() {
            UUID userId = UUID.randomUUID();
            CustomerView customer = new CustomerView(customerId, userId, "Ana", "Gómez",
                    "Cédula de ciudadanía", "1000000001", "ana@example.com", "ACTIVE");
            when(customerApi.getCustomerByUserId(userId)).thenReturn(customer);
            when(accountRepository.findByCustomerIdOrderByCreatedAt(customerId))
                    .thenReturn(List.of(buildAccount(AccountStatusEnum.ACTIVE)));

            assertThat(queryService.getMyAccounts(userId)).hasSize(1);
        }
    }
}
