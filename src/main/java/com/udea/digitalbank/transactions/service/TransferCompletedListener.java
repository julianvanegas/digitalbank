package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.auth.api.UserApi;
import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.shared.utils.AccountNumbers;
import com.udea.digitalbank.shared.utils.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía al ordenante el comprobante de la transferencia por correo.
 *
 * <p>Se ejecuta después de confirmarse la transacción de base de datos, en una transacción propia: un fallo del correo
 * no deshace una transferencia que ya ha movido el dinero. Por eso los errores se registran en el log y no se propagan.
 */
@Slf4j
@Component
public class TransferCompletedListener {

    private static final String SUBJECT = "Comprobante de tu transferencia";

    private final UserApi userApi;
    private final EmailService emailService;

    public TransferCompletedListener(UserApi userApi, EmailService emailService) {
        this.userApi = userApi;
        this.emailService = emailService;
    }

    /**
     * Envía el comprobante de una transferencia completada.
     *
     * @param event datos de la transferencia y usuario al que se le envía
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTransferCompleted(TransferCompletedEvent event) {
        try {
            UserView user = userApi.getUser(event.userId());
            emailService.send(user.email(), SUBJECT, body(event));
        } catch (Exception e) {
            log.error("No se pudo enviar el comprobante de la transferencia {}", event.reference(), e);
        }
    }

    /** Cuerpo del correo. Las cuentas se muestran enmascaradas: solo los últimos dígitos. */
    private static String body(TransferCompletedEvent event) {
        return "Tu transferencia fue exitosa.\n\n"
                + "Referencia: " + event.reference() + "\n"
                + "Monto: " + event.amount().toPlainString() + "\n"
                + "Cuenta de origen: terminada en " + AccountNumbers.lastDigits(event.senderAccountNumber()) + "\n"
                + "Cuenta de destino: terminada en " + AccountNumbers.lastDigits(event.receiverAccountNumber()) + "\n\n"
                + "Si no reconoces esta operación, comunícate con el banco.";
    }
}
