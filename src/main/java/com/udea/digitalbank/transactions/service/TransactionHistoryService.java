package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.shared.exception.InvalidRequestException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotFoundException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.dto.TransactionHistoryEntry;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Historial de movimientos de una cuenta en un rango de fechas, de solo lectura. Un cliente consulta el de sus cuentas
 * y un administrador el de cualquiera.
 *
 * <p>Solo aparecen los movimientos {@code COMPLETED}: son los que movieron dinero, así que los montos con signo
 * explican el saldo. Una transferencia pendiente, rechazada o revertida no es un ingreso ni un egreso.
 */
@Service
public class TransactionHistoryService {

    /** Tope de movimientos por página; un tamaño mayor se recorta a este valor. */
    static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository transactionRepository;
    private final TransactionCatalog catalog;
    private final AccountApi accountApi;

    public TransactionHistoryService(TransactionRepository transactionRepository, TransactionCatalog catalog,
                                     AccountApi accountApi) {
        this.transactionRepository = transactionRepository;
        this.catalog = catalog;
        this.accountApi = accountApi;
    }

    /**
     * Devuelve una página del historial de la cuenta, del movimiento más reciente al más antiguo. Ambas fechas están
     * incluidas: el rango va desde el inicio del día {@code from} hasta el final del día {@code to}. La cuenta se
     * consulta en cualquier estado, también cerrada.
     *
     * <p>Del {@link Pageable} solo se toman la página y el tamaño: el orden es fijo.
     *
     * @param actor         usuario autenticado
     * @param accountNumber número de la cuenta (12 dígitos)
     * @param from          primer día del rango
     * @param to            último día del rango
     * @param pageable      página y tamaño pedidos; el tamaño se recorta a {@link #MAX_PAGE_SIZE}
     * @return los movimientos con monto positivo si entraron y negativo si salieron
     * @throws InvalidRequestException si {@code from} es posterior a {@code to}
     * @throws AccountNotFoundException si la cuenta no existe o, para un cliente, es de otro cliente
     */
    @Transactional(readOnly = true)
    public Page<TransactionHistoryEntry> getHistory(Actor actor, String accountNumber, LocalDate from, LocalDate to,
                                                    Pageable pageable) {
        if (from.isAfter(to)) {
            throw new InvalidRequestException("La fecha inicial no puede ser posterior a la fecha final");
        }
        AccountView account = actor.admin()
                ? accountApi.getAccountByNumber(accountNumber)
                : accountApi.requireOwnedAccount(actor.userId(), accountNumber);

        Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        Page<Transaction> movements = transactionRepository.findCompletedOfAccount(
                account.id(), catalog.status(TransactionStatusEnum.COMPLETED), start, end, page);

        // Varios movimientos suelen ser con la misma contraparte: se consulta una vez por cuenta distinta
        Map<UUID, String> numbers = new HashMap<>();
        return movements.map(movement -> toEntry(movement, account.id(), numbers));
    }

    // Visto desde la cuenta consultada: si es la de origen el dinero salió; si no, entró
    private TransactionHistoryEntry toEntry(Transaction movement, UUID accountId, Map<UUID, String> numbers) {
        boolean outgoing = accountId.equals(movement.getSenderAccountId());
        UUID counterpartId = outgoing ? movement.getReceiverAccountId() : movement.getSenderAccountId();

        TransactionHistoryEntry entry = new TransactionHistoryEntry();
        entry.setReference(movement.getReference());
        entry.setType(movement.getType().getCode());
        entry.setAmount(outgoing ? movement.getAmount().negate() : movement.getAmount());
        entry.setCounterpartAccountNumber(counterpartId == null ? null
                : numbers.computeIfAbsent(counterpartId, id -> accountApi.getAccount(id).accountNumber()));
        entry.setCreatedAt(movement.getCreatedAt());
        return entry;
    }
}
