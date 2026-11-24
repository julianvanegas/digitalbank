package com.udea.digitalbank.transactions.repository;

import com.udea.digitalbank.support.integration.RepositoryTest;
import com.udea.digitalbank.transactions.domain.TransactionLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("TransactionLimitRepository contra PostgreSQL")
class TransactionLimitRepositoryIT extends RepositoryTest {

    @Autowired
    private TransactionLimitRepository repository;

    @Test
    @DisplayName("Lista los topes por tipo de movimiento y luego por tipo de cuenta")
    void findAllOrdered() {
        assertThat(repository.findAllOrdered())
                .extracting(l -> l.getType().getCode(), TransactionLimit::getAccountTypeCode)
                .containsExactly(
                        tuple("TRANSFER", "CHECKING"), tuple("TRANSFER", "SAVINGS"),
                        tuple("WITHDRAWAL", "CHECKING"), tuple("WITHDRAWAL", "SAVINGS"));
    }

    @Test
    @DisplayName("Los retiros tienen cupo diario, tope por operación y tope acumulado")
    void withdrawalLimits() {
        TransactionLimit limit = repository.findByTypeAndAccountType("WITHDRAWAL", "SAVINGS").orElseThrow();

        assertThat(limit.getMaxDailyCount()).isEqualTo(4);
        assertThat(limit.getMaxPerTransaction()).isEqualByComparingTo(new BigDecimal("2500000"));
        assertThat(limit.getMaxDailyAmount()).isEqualByComparingTo(new BigDecimal("10000000"));
    }

    @Test
    @DisplayName("Las transferencias solo tienen tope acumulado diario")
    void transferLimits() {
        TransactionLimit limit = repository.findByTypeAndAccountType("TRANSFER", "CHECKING").orElseThrow();

        assertThat(limit.hasDailyCountLimit()).isFalse();
        assertThat(limit.hasPerTransactionLimit()).isFalse();
        assertThat(limit.getMaxDailyAmount()).isEqualByComparingTo(new BigDecimal("80000000"));
    }

    @Test
    @DisplayName("Las consignaciones no tienen topes definidos")
    void depositHasNoLimits() {
        assertThat(repository.findByTypeAndAccountType("DEPOSIT", "SAVINGS")).isEmpty();
    }
}
