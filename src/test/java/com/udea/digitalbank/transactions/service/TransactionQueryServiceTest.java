package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.shared.exception.transactions.TransactionNotFoundException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.mapper.TransactionMapper;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static com.udea.digitalbank.transactions.TransactionFixtures.RECEIVER_NUMBER;
import static com.udea.digitalbank.transactions.TransactionFixtures.REFERENCE;
import static com.udea.digitalbank.transactions.TransactionFixtures.SENDER_NUMBER;
import static com.udea.digitalbank.transactions.TransactionFixtures.transfer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de {@link TransactionQueryService}: quién puede ver el comprobante de una transacción. */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionQueryService")
class TransactionQueryServiceTest {

    private final UUID ownerId = UUID.randomUUID();
    private final UUID senderId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountApi accountApi;

    private TransactionQueryService queryService;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        queryService = new TransactionQueryService(transactionRepository,
                new TransactionResponses(Mappers.getMapper(TransactionMapper.class), accountApi));
        transaction = transfer(ownerId, senderId, receiverId, "75.00", TransactionStatusEnum.COMPLETED);
        lenient().when(transactionRepository.findByReference(REFERENCE)).thenReturn(Optional.of(transaction));
    }

    private void numbersAreKnown() {
        when(accountApi.getAccount(senderId)).thenReturn(view(senderId, SENDER_NUMBER));
        when(accountApi.getAccount(receiverId)).thenReturn(view(receiverId, RECEIVER_NUMBER));
    }

    private static AccountView view(UUID id, String number) {
        return new AccountView(id, "@AN1231", number, UUID.randomUUID(), AccountStatusEnum.ACTIVE.name(),
                BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Quien ordenó la transacción obtiene su comprobante con los números de cuenta")
    void deberiaEntregarElComprobanteAlOrdenante() {
        numbersAreKnown();

        TransactionResponse response = queryService.getTransaction(new Actor(ownerId, false), REFERENCE);

        assertThat(response.getReference()).isEqualTo(REFERENCE);
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getAmount()).isEqualByComparingTo("75.00");
        assertThat(response.getSenderAccountNumber()).isEqualTo(SENDER_NUMBER);
        assertThat(response.getReceiverAccountNumber()).isEqualTo(RECEIVER_NUMBER);
        assertThat(response.getExpiresAt()).isNull();
    }

    @Test
    @DisplayName("Un administrador obtiene el comprobante de cualquier transacción")
    void deberiaEntregarElComprobanteAUnAdministrador() {
        numbersAreKnown();

        TransactionResponse response = queryService.getTransaction(new Actor(UUID.randomUUID(), true), REFERENCE);

        assertThat(response.getReference()).isEqualTo(REFERENCE);
    }

    @Test
    @DisplayName("La transacción de otro cliente responde igual que una inexistente")
    void deberiaOcultarLaTransaccionDeOtroCliente() {
        assertThatThrownBy(() -> queryService.getTransaction(new Actor(UUID.randomUUID(), false), REFERENCE))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessage("Transacción no encontrada: " + REFERENCE);
    }

    @Test
    @DisplayName("Una referencia inexistente lanza TransactionNotFoundException")
    void deberiaFallarSiNoExiste() {
        when(transactionRepository.findByReference("TRF-NOEXISTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getTransaction(new Actor(ownerId, false), "TRF-NOEXISTE"))
                .isInstanceOf(TransactionNotFoundException.class);
    }
}
