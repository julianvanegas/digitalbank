package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.PurposeCode;
import com.udea.digitalbank.auth.domain.UserAccount;
import com.udea.digitalbank.auth.repository.UserAccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emite el reto EMAIL_CONFIRMATION cuando la transacción que creó la cuenta ya confirmó.
 * Un fallo del correo no deshace la creación: la persona pide otro código con resend-verification.
 */
@Slf4j
@Component
public class AccountCreatedListener {

    private final UserAccountRepository accountRepository;
    private final VerificationService verificationService;
    private final VerificationMailer mailer;

    public AccountCreatedListener(UserAccountRepository accountRepository,
                                  VerificationService verificationService,
                                  VerificationMailer mailer) {
        this.accountRepository = accountRepository;
        this.verificationService = verificationService;
        this.mailer = mailer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAccountCreated(AccountCreatedEvent event) {
        try {
            UserAccount account = accountRepository.findById(event.accountId()).orElseThrow();
            IssuedChallenge challenge = verificationService.issue(account, PurposeCode.EMAIL_CONFIRMATION);
            mailer.send(account.getEmail(), PurposeCode.EMAIL_CONFIRMATION, challenge);
        } catch (Exception e) {
            log.error("No se pudo enviar la confirmación de email de la cuenta {}", event.accountId(), e);
        }
    }
}
