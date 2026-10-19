package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.domain.AccountActionType;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.shared.utils.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link AccountActionListener}: el aviso al titular cuando su cuenta se
 * bloquea o se desbloquea, siempre con el motivo y sin el número completo de la cuenta.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountActionListener")
class AccountActionListenerTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String ACCOUNT_NUMBER = "123456789012";

    @Mock
    private CustomerApi customerApi;
    @Mock
    private EmailService emailService;

    private AccountActionListener listener;

    @BeforeEach
    void setUp() {
        listener = new AccountActionListener(customerApi, emailService);
        CustomerView customer = new CustomerView(CUSTOMER_ID, UUID.randomUUID(), "Ana", "Gómez",
                "Cédula de ciudadanía", "1000000001", "ana@example.com", "ACTIVE");
        // lenient: la última prueba redefine este valor para simular que el titular no se encuentra
        lenient().when(customerApi.getCustomer(CUSTOMER_ID)).thenReturn(customer);
    }

    private String[] sentEmail() {
        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(to.capture(), subject.capture(), body.capture());
        return new String[]{to.getValue(), subject.getValue(), body.getValue()};
    }

    @Test
    @DisplayName("Bloqueo - avisa al titular con el motivo y solo los últimos 4 dígitos de la cuenta")
    void deberiaAvisarElBloqueoConElMotivo() {
        // Act
        listener.onAccountAction(new AccountActionEvent(
                CUSTOMER_ID, ACCOUNT_NUMBER, AccountActionType.BLOCKED, "Actividad sospechosa"));

        // Assert
        String[] email = sentEmail();
        assertThat(email[0]).isEqualTo("ana@example.com");
        assertThat(email[1]).isEqualTo("Tu cuenta fue bloqueada");
        assertThat(email[2]).contains("Hola Ana", "terminada en 9012", "bloqueada", "Motivo: Actividad sospechosa");
        assertThat(email[2]).doesNotContain(ACCOUNT_NUMBER);
    }

    @Test
    @DisplayName("Desbloqueo - avisa al titular con el motivo y solo los últimos 4 dígitos de la cuenta")
    void deberiaAvisarElDesbloqueoConElMotivo() {
        // Act
        listener.onAccountAction(new AccountActionEvent(
                CUSTOMER_ID, ACCOUNT_NUMBER, AccountActionType.UNBLOCKED, "Titular verificado"));

        // Assert
        String[] email = sentEmail();
        assertThat(email[0]).isEqualTo("ana@example.com");
        assertThat(email[1]).isEqualTo("Tu cuenta fue desbloqueada");
        assertThat(email[2]).contains("Hola Ana", "terminada en 9012", "desbloqueada", "Motivo: Titular verificado");
        assertThat(email[2]).doesNotContain(ACCOUNT_NUMBER).doesNotContain("bloqueada temporalmente");
    }

    @Test
    @DisplayName("Cierre - avisa al titular con quién lo hizo y solo los últimos 4 dígitos de la cuenta")
    void deberiaAvisarElCierreConElMotivo() {
        // Act
        listener.onAccountAction(new AccountActionEvent(
                CUSTOMER_ID, ACCOUNT_NUMBER, AccountActionType.CLOSED, "Cierre realizado por un administrador"));

        // Assert
        String[] email = sentEmail();
        assertThat(email[0]).isEqualTo("ana@example.com");
        assertThat(email[1]).isEqualTo("Tu cuenta fue cerrada");
        assertThat(email[2]).contains("Hola Ana", "terminada en 9012", "fue cerrada.",
                "Motivo: Cierre realizado por un administrador");
        assertThat(email[2]).doesNotContain(ACCOUNT_NUMBER).doesNotContain("bloqueada").doesNotContain("desbloqueada");
    }

    @Test
    @DisplayName("Un fallo al enviar el correo no se propaga: el cambio de estado ya está confirmado")
    void noDeberiaPropagarUnFalloDelCorreo() {
        // Arrange
        doThrow(new RuntimeException("Resend caído")).when(emailService).send(anyString(), anyString(), anyString());

        // Act & Assert
        assertThatCode(() -> listener.onAccountAction(new AccountActionEvent(
                CUSTOMER_ID, ACCOUNT_NUMBER, AccountActionType.UNBLOCKED, "Titular verificado")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un fallo al buscar al titular tampoco se propaga ni envía correo")
    void noDeberiaPropagarUnFalloAlBuscarAlTitular() {
        // Arrange
        when(customerApi.getCustomer(CUSTOMER_ID)).thenThrow(new RuntimeException("sin cliente"));

        // Act & Assert
        assertThatCode(() -> listener.onAccountAction(new AccountActionEvent(
                CUSTOMER_ID, ACCOUNT_NUMBER, AccountActionType.BLOCKED, "Fraude")))
                .doesNotThrowAnyException();
        verifyNoInteractions(emailService);
    }
}
