package com.udea.digitalbank.accounts.repository;

import com.udea.digitalbank.accounts.domain.Account;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementación de {@link AccountLocking}.
 * <p>
 * No basta una consulta con {@code @Lock}: si la cuenta ya está en el contexto de persistencia con una versión
 * anterior (porque la transacción la leyó sin bloqueo antes), Hibernate rechaza el resultado de la consulta con
 * {@code StaleObjectStateException} en vez de actualizar la copia, y la operación termina en 409 aunque el bloqueo
 * habría bastado para serializarla. Por eso cada cuenta se bloquea por separado: la que ya está cargada se recarga con
 * el bloqueo ({@code refresh}) y la que no, se lee con él ({@code find}).
 */
class AccountLockingImpl implements AccountLocking {

    // Mismo orden que PostgreSQL da a los uuid (byte a byte, igual que su texto en hexadecimal) y que usan las
    // consultas "order by id" de AccountRepository: todos los bloqueos de cuentas se piden en el mismo orden
    private static final Comparator<UUID> DATABASE_ORDER = Comparator.comparing(UUID::toString);

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Account> findAllByIdForUpdate(Collection<UUID> ids) {
        return ids.stream()
                .distinct()
                .sorted(DATABASE_ORDER)
                .map(this::lock)
                .filter(Objects::nonNull)
                .toList();
    }

    // find no consulta la base si la cuenta ya está cargada; refresh la lee con el bloqueo y trae su estado actual.
    // find con el bloqueo no sirve: sobre una copia cargada solo verifica la versión y falla si cambió
    private Account lock(UUID id) {
        Account loaded = entityManager.find(Account.class, id);
        if (loaded == null) {
            return null;
        }
        entityManager.refresh(loaded, LockModeType.PESSIMISTIC_WRITE);
        return loaded;
    }
}
