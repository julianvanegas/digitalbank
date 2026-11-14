package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.shared.exception.transactions.LimitsNotDefinedException;
import com.udea.digitalbank.transactions.domain.TransactionLimit;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.dto.TransactionLimitResponse;
import com.udea.digitalbank.transactions.repository.TransactionLimitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static com.udea.digitalbank.transactions.TransactionFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas unitarias (AAA) de {@link TransactionLimitQueryService}: la consulta de los topes vigentes. */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionLimitQueryService")
class TransactionLimitQueryServiceTest {

    @Mock
    private TransactionLimitRepository limitRepository;

    private TransactionLimitQueryService service;

    @BeforeEach
    void setUp() {
        service = new TransactionLimitQueryService(limitRepository);
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

    private void allLimitsExist() {
        when(limitRepository.findAllOrdered()).thenReturn(List.of(
                transferLimit("SAVINGS"), transferLimit("CHECKING"), withdrawal("SAVINGS"), withdrawal("CHECKING")));
    }

    @Test
    @DisplayName("Sin tipo indicado devuelve los topes de todos los movimientos (retiros y transferencias) y de todos los tipos de cuenta")
    void deberiaDevolverTodosLosTopes() {
        // Arrange
        allLimitsExist();

        // Act
        List<TransactionLimitResponse> limits = service.getLimits(null, null);

        // Assert
        assertThat(limits).extracting(l -> l.getType() + "/" + l.getAccountType())
                .containsExactly("TRANSFER/SAVINGS", "TRANSFER/CHECKING", "WITHDRAWAL/SAVINGS",
                        "WITHDRAWAL/CHECKING");
    }

    @Test
    @DisplayName("Retiros: informa 4 retiros diarios, $2.500.000 por retiro, $10.000.000 acumulados y que exige 2FA")
    void deberiaInformarLosTopesDeRetiros() {
        // Arrange
        allLimitsExist();

        // Act
        List<TransactionLimitResponse> limits = service.getLimits("WITHDRAWAL", "SAVINGS");

        // Assert
        assertThat(limits).hasSize(1);
        TransactionLimitResponse limit = limits.get(0);
        assertThat(limit.getMaxDailyCount()).isEqualTo(4);
        assertThat(limit.getMaxPerTransaction()).isEqualByComparingTo("2500000");
        assertThat(limit.getMaxDailyAmount()).isEqualByComparingTo("10000000");
        assertThat(limit.getCurrency()).isEqualTo("COP");
        assertThat(limit.isRequiresTwoFactor()).isTrue();
    }

    @Test
    @DisplayName("Transferencias: informa $80.000.000 acumulados al día, sin cupo de cantidad ni tope por operación, y que exige 2FA")
    void deberiaInformarLosTopesDeTransferencias() {
        // Arrange
        allLimitsExist();

        // Act
        List<TransactionLimitResponse> limits = service.getLimits("TRANSFER", null);

        // Assert
        assertThat(limits).hasSize(2).allSatisfy(limit -> {
            assertThat(limit.getType()).isEqualTo("TRANSFER");
            assertThat(limit.getMaxDailyAmount()).isEqualByComparingTo("80000000");
            assertThat(limit.getMaxDailyCount()).isNull();
            assertThat(limit.getMaxPerTransaction()).isNull();
            assertThat(limit.isRequiresTwoFactor()).isTrue();
        });
    }

    @Test
    @DisplayName("Filtra por tipo de cuenta")
    void deberiaFiltrarPorTipoDeCuenta() {
        // Arrange
        allLimitsExist();

        // Act
        List<TransactionLimitResponse> limits = service.getLimits(null, "CHECKING");

        // Assert
        assertThat(limits).extracting(TransactionLimitResponse::getAccountType).containsOnly("CHECKING");
        assertThat(limits).extracting(TransactionLimitResponse::getType)
                .containsExactly("TRANSFER", "WITHDRAWAL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"withdrawal", " Withdrawal ", "WITHDRAWAL"})
    @DisplayName("El tipo se acepta sin importar mayúsculas ni espacios")
    void deberiaNormalizarElTipo(String type) {
        // Arrange
        allLimitsExist();

        // Act + Assert
        assertThat(service.getLimits(type, null)).extracting(TransactionLimitResponse::getType)
                .containsOnly("WITHDRAWAL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Un filtro vacío equivale a no indicar tipo: devuelve todos")
    void deberiaTratarUnFiltroVacioComoSinFiltro(String blank) {
        // Arrange
        allLimitsExist();

        // Act + Assert
        assertThat(service.getLimits(blank, blank)).hasSize(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEPOSIT", "PAYMENT", "xyz"})
    @DisplayName("Un tipo de movimiento sin topes definidos se rechaza e informa que no existen topes para él")
    void deberiaRechazarUnTipoSinTopes(String type) {
        // Arrange
        allLimitsExist();

        // Act + Assert
        assertThatThrownBy(() -> service.getLimits(type, null))
                .isInstanceOf(LimitsNotDefinedException.class)
                .hasMessage("No existen topes definidos para el tipo de operación " + type.toUpperCase());
    }

    @Test
    @DisplayName("Un tipo de cuenta sin topes definidos se rechaza")
    void deberiaRechazarUnTipoDeCuentaSinTopes() {
        // Arrange
        allLimitsExist();

        // Act + Assert
        assertThatThrownBy(() -> service.getLimits(null, "PREMIUM"))
                .isInstanceOf(LimitsNotDefinedException.class)
                .hasMessageContaining("tipo de cuenta PREMIUM");
    }

    @Test
    @DisplayName("Consultar solo lee: no escribe nada en el repositorio")
    void noDeberiaModificarLasReglas() {
        // Arrange
        allLimitsExist();

        // Act
        service.getLimits(null, null);

        // Assert
        verify(limitRepository).findAllOrdered();
        verify(limitRepository, never()).save(any());
        verify(limitRepository, never()).saveAll(any());
        verify(limitRepository, never()).delete(any());
    }
}
