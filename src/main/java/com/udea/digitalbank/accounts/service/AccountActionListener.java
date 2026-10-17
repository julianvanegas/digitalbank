package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.domain.AccountActionType;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.shared.utils.AccountNumbers;
import com.udea.digitalbank.shared.utils.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Avisa al titular de que su cuenta fue bloqueada (CA01), desbloqueada o cerrada, con el motivo. Corre después de
 * confirmarse la transacción: un fallo del correo no deshace el cambio, que es una medida de seguridad y debe
 * quedar aplicada aunque el aviso no salga.
 */
@Slf4j
@Component
public class AccountActionListener {

    private static final String BLOCKED_SUBJECT = "Tu cuenta fue bloqueada";
    private static final String UNLOCKED_SUBJECT = "Tu cuenta fue desbloqueada";
    private static final String CLOSED_SUBJECT = "Tu cuenta fue cerrada";

    private final CustomerApi customerApi;
    private final EmailService emailService;

    public AccountActionListener(CustomerApi customerApi, EmailService emailService) {
        this.customerApi = customerApi;
        this.emailService = emailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAccountAction(AccountActionEvent event) {
        try {
            CustomerView customer = customerApi.getCustomer(event.customerId());
            emailService.send(customer.email(), subject(event.action()), body(customer, event));
        } catch (Exception e) {
            log.error("No se pudo notificar la acción {} de la cuenta {}", event.action(), event.accountNumber(), e);
        }
    }

    private static String subject(AccountActionType action) {
        return switch (action) {
            case BLOCKED -> BLOCKED_SUBJECT;
            case UNLOCKED -> UNLOCKED_SUBJECT;
            case CLOSED -> CLOSED_SUBJECT;
        };
    }

    private static String body(CustomerView customer, AccountActionEvent event) {
        String outcome = switch (event.action()) {
            case BLOCKED -> "fue bloqueada temporalmente.";
            case UNLOCKED -> "fue desbloqueada y ya puedes volver a usarla.";
            case CLOSED -> "fue cerrada.";
        };
        return "Hola " + customer.firstNames() + ",\n\n"
                + "Tu cuenta terminada en " + AccountNumbers.lastDigits(event.accountNumber()) + " " + outcome + "\n\n"
                + "Motivo: " + event.reason() + "\n\n"
                + "Si no reconoces esta acción, comunícate con el banco.";
    }
}
