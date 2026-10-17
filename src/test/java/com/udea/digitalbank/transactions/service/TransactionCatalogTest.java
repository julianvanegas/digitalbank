package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.repository.TransactionStatusRepository;
import com.udea.digitalbank.transactions.repository.TransactionTypeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.udea.digitalbank.transactions.TransactionFixtures.status;
import static com.udea.digitalbank.transactions.TransactionFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** {@link TransactionCatalog} carga los catálogos al arrancar y falla si no coinciden con los enums del código. */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionCatalog")
class TransactionCatalogTest {

    @Mock
    private TransactionStatusRepository statusRepository;
    @Mock
    private TransactionTypeRepository typeRepository;

    private void database(Stream<TransactionStatusEnum> statuses, Stream<TransactionTypeEnum> types) {
        when(statusRepository.findAll()).thenReturn(statuses.map(s -> status(s)).toList());
        when(typeRepository.findAll()).thenReturn(types.map(t -> type(t)).toList());
    }

    @Test
    @DisplayName("Con los catálogos completos resuelve cada estado y tipo por su enum")
    void deberiaResolverCadaValorPorSuEnum() {
        database(Arrays.stream(TransactionStatusEnum.values()), Arrays.stream(TransactionTypeEnum.values()));
        TransactionCatalog catalog = new TransactionCatalog(statusRepository, typeRepository);

        catalog.load();

        assertThat(catalog.status(TransactionStatusEnum.REVERSED).getCode()).isEqualTo("REVERSED");
        assertThat(catalog.type(TransactionTypeEnum.TRANSFER).getCode()).isEqualTo("TRANSFER");
    }

    @Test
    @DisplayName("Si falta un estado en la base, la app no arranca")
    void deberiaFallarSiFaltaUnEstado() {
        database(Stream.of(TransactionStatusEnum.PENDING, TransactionStatusEnum.COMPLETED,
                        TransactionStatusEnum.REJECTED),
                Arrays.stream(TransactionTypeEnum.values()));
        TransactionCatalog catalog = new TransactionCatalog(statusRepository, typeRepository);

        assertThatThrownBy(catalog::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REVERSED");
    }

    @Test
    @DisplayName("Si la base tiene un tipo que el enum no declara, la app no arranca")
    void deberiaFallarSiLaBaseTieneUnTipoDeMas() {
        when(statusRepository.findAll()).thenReturn(
                Arrays.stream(TransactionStatusEnum.values()).map(s -> status(s)).toList());
        when(typeRepository.findAll()).thenReturn(List.of(type(TransactionTypeEnum.TRANSFER),
                type(TransactionTypeEnum.DEPOSIT), type(TransactionTypeEnum.WITHDRAWAL), extraType()));
        TransactionCatalog catalog = new TransactionCatalog(statusRepository, typeRepository);

        assertThatThrownBy(catalog::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REFUND");
    }

    private static com.udea.digitalbank.transactions.domain.TransactionType extraType() {
        var extra = new com.udea.digitalbank.transactions.domain.TransactionType();
        org.springframework.test.util.ReflectionTestUtils.setField(extra, "id", (short) 4);
        org.springframework.test.util.ReflectionTestUtils.setField(extra, "code", "REFUND");
        return extra;
    }
}
