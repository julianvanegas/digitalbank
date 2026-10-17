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

    public TransactionQueryService(TransactionRepository transactionRepository, TransactionResponses responses) {
        this.transactionRepository = transactionRepository;
        this.responses = responses;
    }

    /**
     * Comprobante por referencia. Quien ordenó la transacción la obtiene; un administrador, cualquiera. La de otro
     * cliente responde igual que una inexistente: un 403 confirmaría que la referencia existe.
     */
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Actor actor, String reference) {
        return transactionRepository.findByReference(reference)
                .filter(t -> actor.admin() || t.getUserOriginatorId().equals(actor.userId()))
                .map(responses::of)
                .orElseThrow(() -> new TransactionNotFoundException("Transacción no encontrada: " + reference));
    }
}
