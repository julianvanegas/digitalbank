package com.udea.digitalbank.transactions.mapper;

import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.dto.TransactionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    // Tipo y estado viajan como código. La transacción guarda ids de cuenta, no números: el servicio completa los
    // números (que son lo que el cliente conoce) y, si está pendiente, el vencimiento del código
    @Mapping(target = "type", source = "type.code")
    @Mapping(target = "status", source = "status.code")
    @Mapping(target = "senderAccountNumber", ignore = true)
    @Mapping(target = "receiverAccountNumber", ignore = true)
    @Mapping(target = "expiresAt", ignore = true)
    TransactionResponse toResponse(Transaction transaction);
}
