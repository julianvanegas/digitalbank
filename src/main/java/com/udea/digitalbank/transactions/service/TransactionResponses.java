package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import com.udea.digitalbank.transactions.mapper.TransactionMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Arma el comprobante ({@link TransactionResponse}) de una transacción para {@code TransferService} y
 * {@code TransactionQueryService}. La transacción guarda ids de cuenta pero el ordenante conoce números de cuenta, así
 * que la traducción se hace aquí, en un solo lugar.
 */
@Component
class TransactionResponses {

    private final TransactionMapper mapper;
    private final AccountApi accountApi;

    TransactionResponses(TransactionMapper mapper, AccountApi accountApi) {
        this.mapper = mapper;
        this.accountApi = accountApi;
    }

    /**
     * Arma el comprobante consultando los números de cuenta. Un depósito no tiene cuenta de origen ni un retiro cuenta
     * de destino; en ese caso el número correspondiente queda {@code null}.
     *
     * @param transaction transacción a convertir
     * @return el comprobante, sin vencimiento de código
     */
    TransactionResponse of(Transaction transaction) {
        return of(transaction, numberOf(transaction.getSenderAccountId()),
                numberOf(transaction.getReceiverAccountId()));
    }

    /**
     * Arma el comprobante con los números de cuenta que quien llama ya conoce, sin consultarlos de nuevo.
     *
     * @param transaction    transacción a convertir
     * @param senderNumber   número de la cuenta de origen
     * @param receiverNumber número de la cuenta de destino
     * @return el comprobante, sin vencimiento de código
     */
    TransactionResponse of(Transaction transaction, String senderNumber, String receiverNumber) {
        TransactionResponse response = mapper.toResponse(transaction);
        response.setSenderAccountNumber(senderNumber);
        response.setReceiverAccountNumber(receiverNumber);
        return response;
    }

    /** Número de la cuenta, o {@code null} si la transacción no tiene cuenta de ese lado. */
    private String numberOf(UUID accountId) {
        return accountId == null ? null : accountApi.getAccount(accountId).accountNumber();
    }
}
