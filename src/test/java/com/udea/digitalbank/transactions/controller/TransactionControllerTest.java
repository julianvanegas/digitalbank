package com.udea.digitalbank.transactions.controller;

import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.transactions.dto.ConfirmTransferRequest;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.dto.TransferRequest;
import com.udea.digitalbank.transactions.service.TransactionQueryService;
import com.udea.digitalbank.transactions.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static com.udea.digitalbank.transactions.TransactionFixtures.REFERENCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de TransactionController. Se instancia directamente, sin MockMvc: solo traduce HTTP y pasa el
 * Actor al servicio. Los permisos por rol se prueban contra la cadena de seguridad real en ApiAuthorizationTest.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionController")
class TransactionControllerTest {

    private final Actor customer = new Actor(UUID.randomUUID(), false);
    private final Actor admin = new Actor(UUID.randomUUID(), true);

    @Mock
    private TransferService transferService;
    @Mock
    private TransactionQueryService queryService;

    private TransactionController controller;

    @BeforeEach
    void setUp() {
        controller = new TransactionController(transferService, queryService);
    }

    @Test
    @DisplayName("HU20 CA01 - transfer - responde 201 y pasa el actor del token y la solicitud al servicio")
    void transfer_deberiaDelegarElActorYLaSolicitud() {
        // Arrange
        TransferRequest request = new TransferRequest();
        request.setSenderAccountNumber("100000000001");
        request.setReceiverAccountNumber("200000000002");
        request.setAmount(new BigDecimal("10.00"));
        TransactionResponse expected = new TransactionResponse();
        when(transferService.initiate(customer, request)).thenReturn(expected);

        // Act
        ResponseEntity<TransactionResponse> response = controller.transfer(customer, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
        verify(transferService).initiate(customer, request);
        verifyNoMoreInteractions(transferService);
    }

    @Test
    @DisplayName("HU21 CA01 - confirm - responde 200 y pasa el actor, la referencia y el código al servicio")
    void confirm_deberiaDelegarActorReferenciaYCodigo() {
        // Arrange
        ConfirmTransferRequest request = new ConfirmTransferRequest();
        request.setCode("123456");
        TransactionResponse expected = new TransactionResponse();
        when(transferService.confirm(customer, REFERENCE, "123456")).thenReturn(expected);

        // Act
        ResponseEntity<TransactionResponse> response = controller.confirm(customer, REFERENCE, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(transferService).confirm(customer, REFERENCE, "123456");
    }

    @Test
    @DisplayName("HU20 CA01 - transaction - responde 200 con el comprobante y pasa el actor, sea cliente o administrador")
    void transaction_deberiaDelegarElActorYLaReferencia() {
        // Arrange
        TransactionResponse expected = new TransactionResponse();
        when(queryService.getTransaction(admin, REFERENCE)).thenReturn(expected);

        // Act
        ResponseEntity<TransactionResponse> response = controller.transaction(admin, REFERENCE);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(queryService).getTransaction(admin, REFERENCE);
    }
}
