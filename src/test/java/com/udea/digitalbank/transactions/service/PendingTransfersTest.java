package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.auth.api.VerificationApi;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;

import static com.udea.digitalbank.transactions.TransactionFixtures.status;
import static com.udea.digitalbank.transactions.TransactionFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link PendingTransfers}: el barrido de las transferencias cuyo código venció.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PendingTransfers - HU21 limpieza de transferencias pendientes")
class PendingTransfersTest {

    private static final int CODE_TTL_MINUTES = 3;

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionCatalog catalog;
    @Mock
    private TransactionLimitService limitService;
    @Mock
    private AccountApi accountApi;
    @Mock
    private VerificationApi verificationApi;

    private PendingTransfers service;

    @BeforeEach
    void setUp() {
        service = new PendingTransfers(transactionRepository, catalog, accountApi, verificationApi, Clock.systemDefaultZone());
        lenient().when(catalog.status(any())).thenAnswer(i -> status(i.getArgument(0)));
        lenient().when(catalog.type(any())).thenAnswer(i -> type(i.getArgument(0)));
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(verificationApi.transactionCodeTtlMinutes()).thenReturn(CODE_TTL_MINUTES);
    }

    @Test
    @DisplayName("HU21 CA04 - Rechaza las PENDING más viejas que la vigencia del código y devuelve cuántas fueron")
    void deberiaRechazarLasPendientesVencidas() {
        // Arrange
        when(transactionRepository.rejectPendingCreatedBefore(any(), any(), any())).thenReturn(4);
        LocalDateTime before = LocalDateTime.now();

        // Act
        int rejected = service.rejectExpired();

        // Assert
        assertThat(rejected).isEqualTo(4);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(transactionRepository).rejectPendingCreatedBefore(cutoff.capture(),
                org.mockito.ArgumentMatchers.argThat(s -> s.is(TransactionStatusEnum.PENDING)),
                org.mockito.ArgumentMatchers.argThat(s -> s.is(TransactionStatusEnum.REJECTED)));
        assertThat(cutoff.getValue())
                .isCloseTo(before.minusMinutes(CODE_TTL_MINUTES), within(2, java.time.temporal.ChronoUnit.SECONDS));
    }
}
