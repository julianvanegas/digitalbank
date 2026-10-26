package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.dto.AccountBalanceResponse;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import com.udea.digitalbank.accounts.mapper.AccountMapper;
import com.udea.digitalbank.accounts.repository.AccountRepository;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.shared.security.Actor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.udea.digitalbank.accounts.service.AccountFixtures.ACCOUNT_NUMBER;
import static com.udea.digitalbank.accounts.service.AccountFixtures.buildAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link AccountQueryService} para los criterios de consulta que no tienen prueba en
 * AccountQueryServiceTest: que consultar no modifica la cuenta y que el detalle refleja una cuenta cerrada.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU12 / HU13 - Consultas de saldo y detalle")
class AccountQueryHuTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerApi customerApi;

    private AccountQueryService queryService;
    private final UUID userId = UUID.randomUUID();
    private final Actor owner = new Actor(userId, false);

    @BeforeEach
    void setUp() {
        queryService = new AccountQueryService(accountRepository, customerApi,
                Mappers.getMapper(AccountMapper.class), new AccountLookup(accountRepository));
    }

    private Account ownedAccount(AccountStatusEnum status) {
        Account account = buildAccount(status);
        CustomerView customer = new CustomerView(account.getCustomerId(), userId, "Maria", "Lopez",
                "Cédula de ciudadanía", "1032456123", "maria.lopez@example.com", "ACTIVE");
        when(customerApi.getCustomerByUserId(userId)).thenReturn(customer);
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));
        return account;
    }

    @Test
    @DisplayName("HU12 CA05 - Consultar el saldo varias veces devuelve el mismo valor y no guarda nada")
    void noDeberiaModificarElSaldoAlConsultar() {
        // Arrange
        Account account = ownedAccount(AccountStatusEnum.ACTIVE);
        ReflectionTestUtils.setField(account, "balance", new BigDecimal("500000.00"));

        // Act
        AccountBalanceResponse first = queryService.getBalance(owner, ACCOUNT_NUMBER);
        AccountBalanceResponse second = queryService.getBalance(owner, ACCOUNT_NUMBER);
        AccountBalanceResponse third = queryService.getBalance(owner, ACCOUNT_NUMBER);

        // Assert
        assertThat(first.getBalance()).isEqualByComparingTo("500000.00");
        assertThat(second.getBalance()).isEqualByComparingTo(first.getBalance());
        assertThat(third.getBalance()).isEqualByComparingTo(first.getBalance());
        assertThat(account.getBalance()).isEqualByComparingTo("500000.00");
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU13 CA05 - El detalle de una cuenta cerrada muestra el estado CLOSED y la fecha de cierre")
    void deberiaMostrarLaCuentaCerrada() {
        // Arrange
        Account account = ownedAccount(AccountStatusEnum.CLOSED);
        LocalDateTime closedAt = LocalDateTime.now().minusDays(1);
        ReflectionTestUtils.setField(account, "balance", new BigDecimal("0.00"));
        ReflectionTestUtils.setField(account, "closedAt", closedAt);

        // Act
        AccountResponse response = queryService.getAccount(owner, ACCOUNT_NUMBER);

        // Assert
        assertThat(response.getStatus()).isEqualTo("CLOSED");
        assertThat(response.getClosedAt()).isEqualTo(closedAt);
        assertThat(response.getBalance()).isEqualByComparingTo("0");
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU13 CA01 - El detalle de una cuenta activa muestra número, estado y saldo, sin fecha de cierre")
    void deberiaMostrarElDetalleDeUnaCuentaActiva() {
        // Arrange
        ownedAccount(AccountStatusEnum.ACTIVE);

        // Act
        AccountResponse response = queryService.getAccount(owner, ACCOUNT_NUMBER);

        // Assert
        assertThat(response.getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getBalance()).isEqualByComparingTo("150.75");
        assertThat(response.getClosedAt()).isNull();
    }
}
