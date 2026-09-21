package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emite el reto EMAIL_CONFIRMATION cuando la transacción que creó al usuario ya confirmó.
 * Un fallo del correo no deshace la creación: la persona pide otro código con resend-verification.
 */
@Slf4j
@Component
public class UserCreatedListener {

    private final UserRepository userRepository;
    private final VerificationDispatcher verificationDispatcher;

    public UserCreatedListener(UserRepository userRepository, VerificationDispatcher verificationDispatcher) {
        this.userRepository = userRepository;
        this.verificationDispatcher = verificationDispatcher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserCreated(UserCreatedEvent event) {
        try {
            User user = userRepository.findById(event.userId()).orElseThrow();
            verificationDispatcher.issueAndSend(user, PurposeEnum.EMAIL_CONFIRMATION);
        } catch (Exception e) {
            log.error("No se pudo enviar la confirmación de email del usuario {}", event.userId(), e);
        }
    }
}
