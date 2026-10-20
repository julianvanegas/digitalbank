package com.udea.digitalbank.transactions.mapper;

import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Convierte la entidad {@link Transaction} en el comprobante {@link TransactionResponse} que se entrega al ordenante.
 */
@Mapper(componentModel = "spring")
public interface TransactionMapper {

    /**
     * Mapea los datos propios de la transacción. El tipo y el estado viajan como código ({@code "TRANSFER"},
     * {@code "PENDING"}). Los números de cuenta y el vencimiento del código no se mapean: la entidad guarda ids de
     * cuenta y no conoce el código, así que quien llama los completa.
     *
     * @param transaction transacción a convertir
     * @return comprobante sin números de cuenta ni vencimiento
     */
    @Mapping(target = "type", source = "type.code")
    @Mapping(target = "status", source = "status.code")
    @Mapping(target = "senderAccountNumber", ignore = true)
    @Mapping(target = "receiverAccountNumber", ignore = true)
    @Mapping(target = "expiresAt", ignore = true)
    TransactionResponse toResponse(Transaction transaction);
}
