package com.udea.digitalbank.transactions.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Tareas programadas del módulo {@code transactions}. Esta clase solo decide cuándo se ejecuta cada una; la lógica
 * está en los servicios.
 */
@Slf4j
@Service
public class TransactionScheduledJobs {

    private final TransferService transferService;

    public TransactionScheduledJobs(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * Cada minuto rechaza las transferencias pendientes cuyo código ya ha vencido. Es una limpieza y no una regla de
     * seguridad: una confirmación tardía igualmente se rechaza por código vencido.
     */
    @Scheduled(cron = "0 * * * * *")
    public void rejectExpiredPending() {
        int rejected = transferService.rejectExpiredPending();
        if (rejected > 0) {
            log.info("Transferencias pendientes vencidas rechazadas: {}", rejected);
        }
    }
}
