package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.shared.exception.transactions.TransactionLimitExceededException;
import com.udea.digitalbank.transactions.domain.TransactionLimit;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.repository.TransactionLimitRepository;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.udea.digitalbank.transactions.TransactionFixtures.status;
import static com.udea.digitalbank.transactions.TransactionFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link TransactionLimitService}: el cumplimiento de los topes y la validación de arranque.
 * El reloj es fijo para comprobar la ventana del día calendario. La consulta está en
 * {@code TransactionLimitQueryServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionLimitService")
class TransactionLimitServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T15:30:00Z"), ZoneOffset.UTC);

    @Mock
    private TransactionLimitRepository limitRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionCatalog catalog;

    private TransactionLimitService service;

    private final AccountView savings = account("SAVINGS");

    @BeforeEach
    void setUp() {
        // Las reglas, en el orden en que las aplica la aplicación (@Order)
        DailyUsage usage = new DailyUsage(transactionRepository, catalog);
        List<LimitRule> rules = List.of(new PerTransactionLimitRule(), new DailyCountLimitRule(usage),
                new DailyAmountLimitRule(usage));
        service = new TransactionLimitService(limitRepository, rules, CLOCK);
        lenient().when(catalog.status(any())).thenAnswer(i -> status(i.getArgument(0)));
        lenient().when(catalog.type(any())).thenAnswer(i -> type(i.getArgument(0)));
    }

    private static AccountView account(String accountType) {
        return new AccountView(UUID.randomUUID(), "@AN1231", "100000000001", UUID.randomUUID(), accountType,
                AccountStatusEnum.ACTIVE.name(), new BigDecimal("999999999.00"));
    }

    private static TransactionLimit limit(TransactionTypeEnum typeEnum, String accountType, Integer count,
                                          String perTransaction, String daily) {
        TransactionLimit limit = new TransactionLimit();
        ReflectionTestUtils.setField(limit, "id", (short) 1);
        ReflectionTestUtils.setField(limit, "type", type(typeEnum));
        ReflectionTestUtils.setField(limit, "accountTypeCode", accountType);
        ReflectionTestUtils.setField(limit, "maxDailyCount", count);
        ReflectionTestUtils.setField(limit, "maxPerTransaction", perTransaction == null ? null : new BigDecimal(perTransaction));
        ReflectionTestUtils.setField(limit, "maxDailyAmount", daily == null ? null : new BigDecimal(daily));
        return limit;
    }

    private static TransactionLimit withdrawal(String accountType) {
        return limit(TransactionTypeEnum.WITHDRAWAL, accountType, 4, "2500000.00", "10000000.00");
    }

    private static TransactionLimit transferLimit(String accountType) {
        return limit(TransactionTypeEnum.TRANSFER, accountType, null, null, "80000000.00");
    }

    @Nested
    @DisplayName("requireWithinLimits - cumplimiento de los topes")
    class RequireWithinLimits {

        private void withdrawalLimit() {
            when(limitRepository.findByTypeAndAccountType("WITHDRAWAL", "SAVINGS"))
                    .thenReturn(Optional.of(withdrawal("SAVINGS")));
        }

        private void transferLimitExists() {
            when(limitRepository.findByTypeAndAccountType("TRANSFER", "SAVINGS"))
                    .thenReturn(Optional.of(transferLimit("SAVINGS")));
        }

        private void consumed(long count, String amount) {
            lenient().when(transactionRepository.countCompletedFromAccount(eq(savings.id()), any(), any(), any(), any()))
                    .thenReturn(count);
            lenient().when(transactionRepository.sumCompletedFromAccount(eq(savings.id()), any(), any(), any(), any()))
                    .thenReturn(new BigDecimal(amount));
        }

        @Test
        @DisplayName("Un retiro dentro de los tres topes se acepta, incluido el borde exacto de cada uno")
        void deberiaAceptarUnRetiroEnElBorde() {
            // Arrange: ya hay 3 retiros por 7.500.000; el cuarto, de 2.500.000, llena el día
            withdrawalLimit();
            consumed(3, "7500000.00");

            // Act + Assert
            assertThatCode(() -> service.requireWithinLimits(savings, TransactionTypeEnum.WITHDRAWAL,
                    new BigDecimal("2500000.00"))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Un retiro mayor al máximo por operación se rechaza sin consultar el consumo del día")
        void deberiaRechazarUnRetiroQueSuperaElMaximoPorOperacion() {
            // Arrange
            withdrawalLimit();

            BigDecimal amount = new BigDecimal("2500000.01");

            // Act + Assert
            assertThatThrownBy(() -> service.requireWithinLimits(savings, TransactionTypeEnum.WITHDRAWAL, amount))
                    .isInstanceOf(TransactionLimitExceededException.class)
                    .hasMessageContaining("máximo por operación")
                    .hasMessageContaining("2500000.00 COP");
            verify(transactionRepository, never()).countCompletedFromAccount(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("El quinto retiro del día se rechaza aunque el monto sea pequeño")
        void deberiaRechazarElQuintoRetiroDelDia() {
            // Arrange
            withdrawalLimit();
            consumed(4, "100.00");

            BigDecimal amount = new BigDecimal("1.00");

            // Act + Assert
            assertThatThrownBy(() -> service.requireWithinLimits(savings, TransactionTypeEnum.WITHDRAWAL, amount))
                    .isInstanceOf(TransactionLimitExceededException.class)
                    .hasMessageContaining("4 operaciones diarias");
        }

        @Test
        @DisplayName("Un retiro que haría pasar el acumulado diario se rechaza e informa el cupo disponible")
        void deberiaRechazarUnRetiroQueSuperaElAcumuladoDiario() {
            // Arrange
            withdrawalLimit();
            consumed(2, "9000000.00");

            BigDecimal amount = new BigDecimal("1000000.01");

            // Act + Assert
            assertThatThrownBy(() -> service.requireWithinLimits(savings, TransactionTypeEnum.WITHDRAWAL, amount))
                    .isInstanceOf(TransactionLimitExceededException.class)
                    .hasMessageContaining("10000000.00 COP")
                    .hasMessageContaining("Cupo disponible hoy: 1000000.00 COP");
        }

        @Test
        @DisplayName("Una transferencia que llena exactamente el acumulado de $80.000.000 se acepta")
        void deberiaAceptarUnaTransferenciaQueLlenaElAcumulado() {
            // Arrange
            transferLimitExists();
            consumed(0, "79000000.00");

            // Act + Assert
            assertThatCode(() -> service.requireWithinLimits(savings, TransactionTypeEnum.TRANSFER,
                    new BigDecimal("1000000.00"))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Una transferencia que pasa el acumulado por un centavo se rechaza")
        void deberiaRechazarUnaTransferenciaQueSuperaElAcumulado() {
            // Arrange
            transferLimitExists();
            consumed(0, "79999999.99");

            BigDecimal amount = new BigDecimal("0.02");

            // Act + Assert
            assertThatThrownBy(() -> service.requireWithinLimits(savings, TransactionTypeEnum.TRANSFER, amount))
                    .isInstanceOf(TransactionLimitExceededException.class)
                    .hasMessageContaining("80000000.00 COP")
                    .hasMessageContaining("Cupo disponible hoy: 0.01 COP");
        }

        @Test
        @DisplayName("Las transferencias no tienen cupo de cantidad ni tope por operación: no se consulta el conteo")
        void noDeberiaExigirTopesQueLaTransferenciaNoTiene() {
            // Arrange
            transferLimitExists();
            consumed(500, "0.00");

            // Act
            service.requireWithinLimits(savings, TransactionTypeEnum.TRANSFER, new BigDecimal("79000000.00"));

            // Assert
            verify(transactionRepository, never()).countCompletedFromAccount(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("El consumo se mide sobre el día calendario del reloj, solo de transacciones COMPLETED y de la cuenta de origen")
        void deberiaMedirElConsumoDelDiaCalendario() {
            // Arrange
            transferLimitExists();
            consumed(0, "0.00");

            // Act
            service.requireWithinLimits(savings, TransactionTypeEnum.TRANSFER, new BigDecimal("10.00"));

            // Assert
            ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(transactionRepository).sumCompletedFromAccount(eq(savings.id()),
                    org.mockito.ArgumentMatchers.argThat(t -> t.is(TransactionTypeEnum.TRANSFER)),
                    org.mockito.ArgumentMatchers.argThat(s -> s.is(com.udea.digitalbank.transactions.domain.TransactionStatusEnum.COMPLETED)),
                    from.capture(), to.capture());
            assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 14, 0, 0));
            assertThat(to.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 15, 0, 0));
        }

        @Test
        @DisplayName("Un tipo de movimiento limitado sin tope para el tipo de cuenta es un error de configuración, no un permiso")
        void deberiaFallarSiFaltaElTopeDeConfiguracion() {
            // Arrange
            when(limitRepository.findByTypeAndAccountType(anyString(), anyString())).thenReturn(Optional.empty());

            BigDecimal amount = new BigDecimal("1.00");

            // Act + Assert
            assertThatThrownBy(() -> service.requireWithinLimits(savings, TransactionTypeEnum.TRANSFER, amount))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("validate - arranque")
    class Validate {

        @Test
        @DisplayName("Con topes para cada tipo de movimiento limitado y cada tipo de cuenta, arranca")
        void deberiaArrancarConLaConfiguracionCompleta() {
            // Arrange
            when(limitRepository.findByTypeAndAccountType(anyString(), anyString()))
                    .thenReturn(Optional.of(transferLimit("SAVINGS")));

            // Act + Assert
            assertThatCode(() -> service.validate()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Si falta una combinación, no arranca y dice cuál")
        void deberiaFallarSiFaltaUnaCombinacion() {
            // Arrange
            when(limitRepository.findByTypeAndAccountType(anyString(), anyString()))
                    .thenReturn(Optional.of(transferLimit("SAVINGS")));
            when(limitRepository.findByTypeAndAccountType("WITHDRAWAL", "CHECKING")).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> service.validate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("WITHDRAWAL / CHECKING");
        }
    }
}
