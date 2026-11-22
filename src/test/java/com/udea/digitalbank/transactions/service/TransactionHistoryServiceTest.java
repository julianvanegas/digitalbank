package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.shared.exception.InvalidRequestException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatus;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.dto.TransactionHistoryEntry;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.udea.digitalbank.transactions.TransactionFixtures.RECEIVER_NUMBER;
import static com.udea.digitalbank.transactions.TransactionFixtures.SENDER_NUMBER;
import static com.udea.digitalbank.transactions.TransactionFixtures.status;
import static com.udea.digitalbank.transactions.TransactionFixtures.transfer;
import static com.udea.digitalbank.transactions.TransactionFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias (AAA) de {@link TransactionHistoryService}: quién ve el historial de una cuenta, qué rango de
 * fechas se consulta y con qué signo se muestra cada movimiento.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionHistoryService")
class TransactionHistoryServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);
    private static final Pageable PAGE = PageRequest.of(0, 20);

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID otherAccountId = UUID.randomUUID();
    private final Actor customer = new Actor(userId, false);
    private final Actor admin = new Actor(UUID.randomUUID(), true);
    private final TransactionStatus completed = status(TransactionStatusEnum.COMPLETED);

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionCatalog catalog;
    @Mock
    private AccountApi accountApi;

    private TransactionHistoryService service;

    @BeforeEach
    void setUp() {
        service = new TransactionHistoryService(transactionRepository, catalog, accountApi);
    }

    private static AccountView view(UUID id, String number) {
        return new AccountView(id, "@AN1231", number, UUID.randomUUID(), "SAVINGS",
                AccountStatusEnum.ACTIVE.name(), BigDecimal.ZERO);
    }

    // La cuenta consultada es del cliente y el repositorio devuelve los movimientos indicados
    private void ownAccountWith(Transaction... movements) {
        when(accountApi.requireOwnedAccount(userId, SENDER_NUMBER)).thenReturn(view(accountId, SENDER_NUMBER));
        repositoryReturns(movements);
    }

    private void repositoryReturns(Transaction... movements) {
        when(catalog.status(TransactionStatusEnum.COMPLETED)).thenReturn(completed);
        when(transactionRepository.findCompletedOfAccount(eq(accountId), eq(completed), any(), any(), any()))
                .thenAnswer(call -> new PageImpl<>(List.of(movements), call.getArgument(4), movements.length));
    }

    private static Transaction deposit(UUID receiverId, String amount) {
        return Transaction.deposit(UUID.randomUUID(), UUID.randomUUID(), receiverId, new BigDecimal(amount),
                "DEP-20261015-7Xk2Pq9RmZ4tNvB8cHdWeA", type(TransactionTypeEnum.DEPOSIT),
                status(TransactionStatusEnum.COMPLETED), LocalDateTime.of(2026, 10, 15, 9, 0));
    }

    private static Transaction withdrawal(UUID senderId, String amount) {
        Transaction withdrawal = Transaction.withdrawal(UUID.randomUUID(), UUID.randomUUID(), senderId,
                new BigDecimal(amount), "RET-20261016-7Xk2Pq9RmZ4tNvB8cHdWeA", type(TransactionTypeEnum.WITHDRAWAL),
                status(TransactionStatusEnum.PENDING), LocalDateTime.of(2026, 10, 16, 9, 0));
        withdrawal.complete(status(TransactionStatusEnum.COMPLETED));
        return withdrawal;
    }

    @Test
    @DisplayName("Un egreso (transferencia enviada) sale con monto negativo y el número de la cuenta destino")
    void deberiaMostrarLaTransferenciaEnviadaComoNegativa() {
        ownAccountWith(transfer(userId, accountId, otherAccountId, "75.00", TransactionStatusEnum.COMPLETED));
        when(accountApi.getAccount(otherAccountId)).thenReturn(view(otherAccountId, RECEIVER_NUMBER));

        Page<TransactionHistoryEntry> history = service.getHistory(customer, SENDER_NUMBER, FROM, TO, PAGE);

        assertThat(history.getContent()).singleElement().satisfies(entry -> {
            assertThat(entry.getAmount()).isEqualByComparingTo("-75.00");
            assertThat(entry.getType()).isEqualTo("TRANSFER");
            assertThat(entry.getCounterpartAccountNumber()).isEqualTo(RECEIVER_NUMBER);
        });
    }

    @Test
    @DisplayName("Un ingreso (transferencia recibida) sale con monto positivo y el número de la cuenta origen")
    void deberiaMostrarLaTransferenciaRecibidaComoPositiva() {
        ownAccountWith(transfer(UUID.randomUUID(), otherAccountId, accountId, "40.50", TransactionStatusEnum.COMPLETED));
        when(accountApi.getAccount(otherAccountId)).thenReturn(view(otherAccountId, RECEIVER_NUMBER));

        Page<TransactionHistoryEntry> history = service.getHistory(customer, SENDER_NUMBER, FROM, TO, PAGE);

        assertThat(history.getContent()).singleElement().satisfies(entry -> {
            assertThat(entry.getAmount()).isEqualByComparingTo("40.50");
            assertThat(entry.getCounterpartAccountNumber()).isEqualTo(RECEIVER_NUMBER);
        });
    }

    @Test
    @DisplayName("Un depósito es positivo y un retiro negativo, ambos sin cuenta contraparte")
    void deberiaMostrarDepositosYRetirosSinContraparte() {
        ownAccountWith(deposit(accountId, "200.00"), withdrawal(accountId, "30.00"));

        Page<TransactionHistoryEntry> history = service.getHistory(customer, SENDER_NUMBER, FROM, TO, PAGE);

        assertThat(history.getContent()).satisfiesExactly(
                deposit -> {
                    assertThat(deposit.getType()).isEqualTo("DEPOSIT");
                    assertThat(deposit.getAmount()).isEqualByComparingTo("200.00");
                    assertThat(deposit.getCounterpartAccountNumber()).isNull();
                },
                withdrawal -> {
                    assertThat(withdrawal.getType()).isEqualTo("WITHDRAWAL");
                    assertThat(withdrawal.getAmount()).isEqualByComparingTo("-30.00");
                    assertThat(withdrawal.getCounterpartAccountNumber()).isNull();
                });
        verify(accountApi, never()).getAccount(any());
    }

    @Test
    @DisplayName("El número de una misma contraparte se consulta una sola vez por consulta")
    void deberiaConsultarUnaVezCadaContraparte() {
        ownAccountWith(
                transfer(userId, accountId, otherAccountId, "10.00", TransactionStatusEnum.COMPLETED),
                transfer(userId, accountId, otherAccountId, "20.00", TransactionStatusEnum.COMPLETED),
                transfer(userId, otherAccountId, accountId, "30.00", TransactionStatusEnum.COMPLETED));
        when(accountApi.getAccount(otherAccountId)).thenReturn(view(otherAccountId, RECEIVER_NUMBER));

        Page<TransactionHistoryEntry> history = service.getHistory(customer, SENDER_NUMBER, FROM, TO, PAGE);

        assertThat(history.getContent()).extracting(TransactionHistoryEntry::getAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("-10.00"), new BigDecimal("-20.00"), new BigDecimal("30.00"));
        verify(accountApi, times(1)).getAccount(otherAccountId);
    }

    @Test
    @DisplayName("Un cliente consulta sus cuentas: se valida que la cuenta sea suya")
    void deberiaExigirQueLaCuentaSeaDelCliente() {
        ownAccountWith();

        service.getHistory(customer, SENDER_NUMBER, FROM, TO, PAGE);

        verify(accountApi).requireOwnedAccount(userId, SENDER_NUMBER);
        verify(accountApi, never()).getAccountByNumber(any());
    }

    @Test
    @DisplayName("Una cuenta de otro cliente responde igual que una inexistente y no consulta movimientos")
    void deberiaOcultarLaCuentaDeOtroCliente() {
        when(accountApi.requireOwnedAccount(userId, SENDER_NUMBER))
                .thenThrow(new AccountNotFoundException("Cuenta no encontrada: " + SENDER_NUMBER));

        assertThatThrownBy(() -> service.getHistory(customer, SENDER_NUMBER, FROM, TO, PAGE))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Un administrador consulta la cuenta de cualquier cliente sin pasar por la titularidad")
    void deberiaPermitirAlAdministradorConsultarCualquierCuenta() {
        when(accountApi.getAccountByNumber(SENDER_NUMBER)).thenReturn(view(accountId, SENDER_NUMBER));
        repositoryReturns(deposit(accountId, "10.00"));

        Page<TransactionHistoryEntry> history = service.getHistory(admin, SENDER_NUMBER, FROM, TO, PAGE);

        assertThat(history.getContent()).hasSize(1);
        verify(accountApi, never()).requireOwnedAccount(any(), any());
    }

    @Test
    @DisplayName("El rango incluye ambos días: desde el inicio de 'from' hasta el inicio del día siguiente a 'to'")
    void deberiaConvertirElRangoDeDiasEnUnIntervaloSemiabierto() {
        ownAccountWith();

        service.getHistory(customer, SENDER_NUMBER, FROM, TO, PAGE);

        verify(transactionRepository).findCompletedOfAccount(eq(accountId), eq(completed),
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)), eq(LocalDateTime.of(2026, 11, 1, 0, 0)), any());
    }

    @Test
    @DisplayName("Un solo día es un rango válido")
    void deberiaAceptarUnRangoDeUnSoloDia() {
        ownAccountWith();

        service.getHistory(customer, SENDER_NUMBER, FROM, FROM, PAGE);

        verify(transactionRepository).findCompletedOfAccount(eq(accountId), eq(completed),
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)), eq(LocalDateTime.of(2026, 10, 2, 0, 0)), any());
    }

    @Test
    @DisplayName("Una fecha inicial posterior a la final lanza InvalidRequestException sin consultar nada")
    void deberiaRechazarUnRangoInvertido() {
        assertThatThrownBy(() -> service.getHistory(customer, SENDER_NUMBER, TO, FROM, PAGE))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("La fecha inicial no puede ser posterior a la fecha final");
        verifyNoInteractions(accountApi, transactionRepository);
    }

    @Test
    @DisplayName("El tamaño de página se recorta al máximo y el orden pedido se ignora")
    void deberiaRecortarElTamanoYFijarElOrden() {
        ownAccountWith();
        Pageable requested = PageRequest.of(2, 5_000, Sort.by("amount"));

        service.getHistory(customer, SENDER_NUMBER, FROM, TO, requested);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findCompletedOfAccount(any(), any(), any(), any(), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getPageSize()).isEqualTo(TransactionHistoryService.MAX_PAGE_SIZE);
        assertThat(page.getValue().getSort()).isEqualTo(Sort.unsorted());
    }
}
