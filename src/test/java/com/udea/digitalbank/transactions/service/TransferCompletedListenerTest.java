package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.auth.api.UserApi;
import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.shared.utils.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static com.udea.digitalbank.transactions.TransactionFixtures.RECEIVER_NUMBER;
import static com.udea.digitalbank.transactions.TransactionFixtures.REFERENCE;
import static com.udea.digitalbank.transactions.TransactionFixtures.SENDER_NUMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferCompletedListener")
class TransferCompletedListenerTest {

    private final UUID userId = UUID.randomUUID();
    private final TransferCompletedEvent event = new TransferCompletedEvent(userId, REFERENCE, SENDER_NUMBER,
            RECEIVER_NUMBER, new BigDecimal("120.50"));

    @Mock
    private UserApi userApi;
    @Mock
    private EmailService emailService;

    @Test
    @DisplayName("Envía el comprobante al correo del ordenante, con las cuentas enmascaradas")
    void deberiaEnviarElComprobanteConLasCuentasEnmascaradas() {
        when(userApi.getUser(userId)).thenReturn(new UserView(userId, "ana@example.com", "CUSTOMER", "ACTIVE"));

        new TransferCompletedListener(userApi, emailService).onTransferCompleted(event);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("ana@example.com"), eq("Comprobante de tu transferencia"), body.capture());
        assertThat(body.getValue()).contains(REFERENCE, "120.50", "0001", "0002")
                .doesNotContain(SENDER_NUMBER, RECEIVER_NUMBER);
    }

    @Test
    @DisplayName("Un fallo al consultar el usuario o enviar el correo no se propaga: la transferencia ya se hizo")
    void noDeberiaPropagarUnFalloDelCorreo() {
        when(userApi.getUser(userId)).thenReturn(new UserView(userId, "ana@example.com", "CUSTOMER", "ACTIVE"));
        doThrow(new IllegalStateException("sin correo")).when(emailService).send(anyString(), anyString(), anyString());

        assertThatCode(() -> new TransferCompletedListener(userApi, emailService).onTransferCompleted(event))
                .doesNotThrowAnyException();
    }
}
