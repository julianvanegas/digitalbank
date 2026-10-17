package com.udea.digitalbank.accounts.mapper;

import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.dto.AccountBalanceResponse;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    // Estado y tipo viajan como código ("ACTIVE", "SAVINGS"), no como el id de la fila del catálogo
    @Mapping(target = "status", source = "status.code")
    @Mapping(target = "accountType", source = "accountType.code")
    AccountResponse toResponse(Account account);

    // Número y saldo se llaman igual en la entidad; el estado viaja como código, igual que en toResponse
    @Mapping(target = "status", source = "status.code")
    AccountBalanceResponse toBalanceResponse(Account account);

    // Vista para otros módulos (AccountApi): sin las marcas de tiempo ni la posición en el cupo
    @Mapping(target = "status", source = "status.code")
    AccountView toView(Account account);
}
