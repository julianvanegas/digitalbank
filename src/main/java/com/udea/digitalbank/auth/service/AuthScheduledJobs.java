package com.udea.digitalbank.auth.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Todo lo que auth ejecuta por reloj. Solo decide cuándo: la lógica vive en los servicios,
 * igual que un controller solo recibe la petición y delega.
 */
@Service
public class AuthScheduledJobs {

    private final VerificationService verificationService;
    private final UserStatusService userStatusService;
    private final SessionService sessionService;

    public AuthScheduledJobs(VerificationService verificationService,
                             UserStatusService userStatusService,
                             SessionService sessionService) {
        this.verificationService = verificationService;
        this.userStatusService = userStatusService;
        this.sessionService = sessionService;
    }

    // Higiene, no seguridad: los códigos y las sesiones vencidos ya se rechazan al usarlos
    @Scheduled(cron = "0 */15 * * * *")
    public void cleanExpired() {
        verificationService.deleteExpired();
        sessionService.deleteExpired();
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void markInactive() {
        userStatusService.markInactiveCustomers();
    }
}
