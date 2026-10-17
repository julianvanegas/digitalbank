package com.udea.digitalbank.transactions.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Todo lo que transactions ejecuta por reloj. Solo decide cuándo: la lógica vive en los servicios.
 */
@Slf4j
@Service
public class TransactionScheduledJobs {

    private final TransferService transferService;

    public TransactionScheduledJobs(TransferService transferService) {
        this.transferService = transferService;
    }

    // Higiene, no seguridad: una confirmación tardía ya se rechaza por código vencido
    @Scheduled(cron = "0 * * * * *")
    public void rejectExpiredPending() {
        int rejected = transferService.rejectExpiredPending();
        if (rejected > 0) {
            log.info("Transferencias pendientes vencidas rechazadas: {}", rejected);
        }
    }
}
