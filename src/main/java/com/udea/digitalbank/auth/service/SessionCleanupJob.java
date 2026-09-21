package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.repository.AuthSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class SessionCleanupJob {

    private final AuthSessionRepository sessionRepository;

    public SessionCleanupJob(AuthSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    @Scheduled(cron = "0 */15 * * * *")
    public void deleteExpiredSessions() {
        sessionRepository.deleteExpired(LocalDateTime.now());
    }
}
