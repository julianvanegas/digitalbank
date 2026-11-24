package com.udea.digitalbank.accounts.repository;

import com.udea.digitalbank.accounts.domain.Account;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Bloqueo de cuentas para mover dinero. Lo implementa {@link AccountLockingImpl}. */
public interface AccountLocking {

    /**
     * Cuentas con la fila bloqueada para escritura ({@code SELECT ... FOR UPDATE}) y con su estado actual, siempre en
     * el orden de sus ids. Dos transferencias cruzadas entre las mismas cuentas piden los bloqueos en el mismo orden y
     * se serializan, en lugar de quedar en un interbloqueo.
     * <p>
     * Una cuenta que la transacción ya había leído sin bloqueo (para validarla o consultar sus topes) se recarga con
     * el bloqueo: si otra transacción la cambió mientras esta esperaba, se devuelve el saldo y la versión de ahora. Por
     * eso esas cuentas no deben tener cambios pendientes al llamar a este método, porque se descartarían.
     *
     * @param ids identificadores de las cuentas a bloquear
     * @return las cuentas encontradas, ordenadas por id
     */
    List<Account> findAllByIdForUpdate(Collection<UUID> ids);
}
