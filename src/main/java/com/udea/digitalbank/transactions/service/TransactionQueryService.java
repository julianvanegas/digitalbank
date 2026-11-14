package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.shared.exception.transactions.TransactionNotFoundException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas de transacciones, de solo lectura: el comprobante de una transferencia por su referencia. */
@Service
public class TransactionQueryService {

    private final TransactionRepository transactionRepository;
    private final TransactionResponses responses;
    private final TransactionAccess access;

    public TransactionQueryService(TransactionRepository transactionRepository, TransactionResponses responses,
                                   TransactionAccess access) {
        this.transactionRepository = transactionRepository;
        this.responses = responses;
        this.access = access;
    }

    /**
     * Devuelve el comprobante de una transacción por su referencia, a quien puede verla según {@link TransactionAccess}.
     * La de otro usuario responde igual que una inexistente, porque un 403 confirmaría que la referencia existe.
     *
     * @param actor     usuario autenticado
     * @param reference comprobante de la transacción
     * @return el comprobante con los números de cuenta
     * @throws TransactionNotFoundException si no existe o el usuario no tiene acceso a ella
     */
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Actor actor, String reference) {
        return transactionRepository.findByReference(reference)
                .filter(t -> access.canView(actor, t))
                .map(responses::of)
                .orElseThrow(() -> new TransactionNotFoundException("Transacción no encontrada: " + reference));
    }
}
