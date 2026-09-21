package com.udea.digitalbank.auth.api;

import com.udea.digitalbank.auth.domain.CategoryCode;
import com.udea.digitalbank.auth.domain.RoleCode;
import com.udea.digitalbank.auth.domain.StatusCode;
import com.udea.digitalbank.auth.domain.UserAccount;
import com.udea.digitalbank.auth.repository.UserAccountRepository;
import com.udea.digitalbank.auth.service.AccountCreatedEvent;
import com.udea.digitalbank.auth.service.AccountStatusService;
import com.udea.digitalbank.auth.service.Catalogs;
import com.udea.digitalbank.shared.exception.AccountNotFoundException;
import com.udea.digitalbank.shared.exception.DuplicateAccountException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Única puerta de entrada de otros módulos a auth.
 * Los módulos solo guardan el id de la cuenta; nunca importan entidades ni repositorios de auth.
 */
@Service
public class AuthFacade {

    private final UserAccountRepository accountRepository;
    private final AccountStatusService accountStatusService;
    private final Catalogs catalogs;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public AuthFacade(UserAccountRepository accountRepository,
                      AccountStatusService accountStatusService,
                      Catalogs catalogs,
                      PasswordEncoder passwordEncoder,
                      ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.accountStatusService = accountStatusService;
        this.catalogs = catalogs;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Participa en la transacción del llamante: si el perfil falla, la cuenta se revierte.
     * Deja la cuenta en (categoría, PENDING_VERIFICATION) y, al confirmarse la transacción,
     * se emite el reto EMAIL_CONFIRMATION.
     */
    @Transactional
    public AccountView createAccount(String email, String password, RoleCode roleCode, CategoryCode categoryCode) {
        if (accountRepository.existsByEmail(email)) {
            throw new DuplicateAccountException("Ya existe una cuenta registrada con ese email");
        }

        LocalDateTime now = LocalDateTime.now();
        UserAccount account = new UserAccount();
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setPasswordChangedAt(now);
        account.setRole(catalogs.role(roleCode));
        account.setStatus(catalogs.status(categoryCode, StatusCode.PENDING_VERIFICATION));
        account.setCreatedAt(now);
        UserAccount saved = accountRepository.save(account);

        eventPublisher.publishEvent(new AccountCreatedEvent(saved.getId()));
        return toView(saved);
    }

    @Transactional
    public void changeStatus(Long accountId, StatusCode statusCode) {
        accountStatusService.changeStatus(accountId, statusCode);
    }

    @Transactional
    public void revokeSession(Long accountId) {
        accountStatusService.revokeSession(accountId);
    }

    @Transactional(readOnly = true)
    public AccountView getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .map(AuthFacade::toView)
                .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada: " + accountId));
    }

    @Transactional(readOnly = true)
    public List<AccountView> getAccounts(Collection<Long> accountIds) {
        return accountRepository.findAllById(accountIds).stream().map(AuthFacade::toView).toList();
    }

    private static AccountView toView(UserAccount account) {
        return new AccountView(account.getId(), account.getEmail(),
                account.getRole().getCode(), account.getStatus().getCode());
    }
}
