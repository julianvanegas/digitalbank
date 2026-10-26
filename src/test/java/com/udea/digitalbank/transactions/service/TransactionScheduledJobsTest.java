package com.udea.digitalbank.transactions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link TransactionScheduledJobs}: cada minuto cancela las transferencias pendientes
 * cuyo código ya venció, para que no queden pendientes para siempre si el cliente nunca confirma.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU21 - TransactionScheduledJobs")
class TransactionScheduledJobsTest {

    @Mock
    private TransferService transferService;

    @InjectMocks
    private TransactionScheduledJobs jobs;

    @Test
    @DisplayName("HU21 CA04 - Cancela las transferencias pendientes vencidas, haya o no alguna")
    void deberiaCancelarLasTransferenciasVencidas() {
        // Arrange
        when(transferService.rejectExpiredPending()).thenReturn(2, 0);

        // Act + Assert
        assertThatCode(() -> {
            jobs.rejectExpiredPending();
            jobs.rejectExpiredPending();
        }).doesNotThrowAnyException();
        verify(transferService, org.mockito.Mockito.times(2)).rejectExpiredPending();
    }

    @Test
    @DisplayName("HU21 CA04 - La cancelación de pendientes vencidas corre cada minuto")
    void deberiaCorrerCadaMinuto() throws NoSuchMethodException {
        // Act
        Scheduled scheduled = TransactionScheduledJobs.class.getMethod("rejectExpiredPending")
                .getAnnotation(Scheduled.class);

        // Assert
        assertThat(scheduled.cron()).isEqualTo("0 * * * * *");
    }
}
