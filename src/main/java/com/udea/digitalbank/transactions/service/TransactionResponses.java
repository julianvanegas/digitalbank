package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.mapper.TransactionMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Arma el comprobante de una transacción para TransferService y TransactionQueryService. La transacción guarda ids
 * de cuenta y el cliente conoce números, así que el número se completa aquí, en un solo lugar.
 */
@Component
class TransactionResponses {

    private final TransactionMapper mapper;
    private final AccountApi accountApi;

    TransactionResponses(TransactionMapper mapper, AccountApi accountApi) {
        this.mapper = mapper;
        this.accountApi = accountApi;
    }

    /** Busca los números de cuenta; un depósito no tiene emisora ni un retiro receptora, y esa queda nula. */
    TransactionResponse of(Transaction transaction) {
        return of(transaction, numberOf(transaction.getSenderAccountId()),
                numberOf(transaction.getReceiverAccountId()));
    }

    /** Para quien ya tiene los números a mano y evita consultarlos de nuevo. */
    TransactionResponse of(Transaction transaction, String senderNumber, String receiverNumber) {
        TransactionResponse response = mapper.toResponse(transaction);
        response.setSenderAccountNumber(senderNumber);
        response.setReceiverAccountNumber(receiverNumber);
        return response;
    }

    private String numberOf(UUID accountId) {
        return accountId == null ? null : accountApi.getAccount(accountId).accountNumber();
    }
}
