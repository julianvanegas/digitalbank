package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.StatusCode;
import com.udea.digitalbank.auth.domain.CategoryCode;
import com.udea.digitalbank.auth.domain.UserAccount;
import com.udea.digitalbank.auth.repository.AuthSessionRepository;
import com.udea.digitalbank.auth.repository.UserAccountRepository;
import com.udea.digitalbank.shared.exception.AccountNotFoundException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AccountStatusService {

    static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int INACTIVITY_MONTHS = 12;

    private final UserAccountRepository accountRepository;
    private final AuthSessionRepository sessionRepository;
    private final Catalogs catalogs;

    public AccountStatusService(UserAccountRepository accountRepository,
                                AuthSessionRepository sessionRepository,
                                Catalogs catalogs) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.catalogs = catalogs;
    }

    // El estado nuevo se busca dentro de la categoría de la cuenta; si no existe, se rechaza
    @Transactional
    public void changeStatus(Long accountId, StatusCode newStatusCode) {
        UserAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada: " + accountId));
        applyStatus(account, newStatusCode);
    }

    // Suma un fallo y bloquea al llegar al máximo
    @Transactional
    public void registerFailedAttempt(UserAccount account) {
        account.setFailedAttempts(account.getFailedAttempts() + 1);
        if (account.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            applyStatus(account, StatusCode.BLOCKED);
        }
        accountRepository.save(account);
    }

    @Transactional
    public void revokeSession(Long accountId) {
        sessionRepository.deleteByAccount(accountId);
    }

    // Solo mueve (CUSTOMER, ACTIVE) -> (CUSTOMER, INACTIVE): el personal queda fuera por construcción
    @Transactional
    @Scheduled(cron = "0 0 2 * * *")
    public void markInactiveCustomers() {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(INACTIVITY_MONTHS);
        accountRepository.findIdle(CategoryCode.CUSTOMER.name(), StatusCode.ACTIVE.name(), cutoff)
                .forEach(account -> {
                    applyStatus(account, StatusCode.INACTIVE);
                    accountRepository.save(account);
                });
    }

    private void applyStatus(UserAccount account, StatusCode statusCode) {
        CategoryCode category = CategoryCode.valueOf(account.getStatus().getCategory().getCode());
        account.setStatus(catalogs.status(category, statusCode));
        if (statusCode == StatusCode.ACTIVE) {
            account.setFailedAttempts(0);
        } else {
            sessionRepository.deleteByAccount(account.getId());
        }
        accountRepository.save(account);
    }
}
