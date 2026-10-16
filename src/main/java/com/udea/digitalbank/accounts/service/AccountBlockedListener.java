package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.shared.utils.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Avisa al titular de que su cuenta quedó bloqueada (CA01). Corre después de confirmarse la
 * transacción: un fallo del correo no deshace el bloqueo, que es una medida de seguridad y debe
 * quedar aplicada aunque el aviso no salga.
 */
@Slf4j
@Component
public class AccountBlockedListener {

    private static final String SUBJECT = "Tu cuenta fue bloqueada";
    // Solo los últimos dígitos: el titular distingue cuál de sus cuentas es sin que el correo
    // lleve el número completo
    private static final int VISIBLE_DIGITS = 4;

    private final CustomerApi customerApi;
    private final EmailService emailService;

    public AccountBlockedListener(CustomerApi customerApi, EmailService emailService) {
        this.customerApi = customerApi;
        this.emailService = emailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAccountBlocked(AccountBlockedEvent event) {
        try {
            CustomerView customer = customerApi.getCustomer(event.customerId());
            emailService.send(customer.email(), SUBJECT, body(customer, event));
        } catch (Exception e) {
            log.error("No se pudo notificar el bloqueo de la cuenta {}", event.accountNumber(), e);
        }
    }

    private static String body(CustomerView customer, AccountBlockedEvent event) {
        return "Hola " + customer.firstNames() + ",\n\n"
                + "Tu cuenta terminada en " + lastDigits(event.accountNumber())
                + " fue bloqueada temporalmente.\n\n"
                + "Motivo: " + event.reason() + "\n\n"
                + "Si no reconoces esta acción, comunícate con el banco.";
    }

    private static String lastDigits(String accountNumber) {
        return accountNumber.length() <= VISIBLE_DIGITS
                ? accountNumber
                : accountNumber.substring(accountNumber.length() - VISIBLE_DIGITS);
    }
}
