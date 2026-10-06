package com.udea.digitalbank.accounts.mapper;

import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.dto.AccountResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    // El estado viaja como código ("ACTIVE"), no como el id de la fila del catálogo
    @Mapping(target = "status", source = "status.code")
    AccountResponse toResponse(Account account);

    // Vista para otros módulos (AccountApi): sin las marcas de tiempo ni la posición en el cupo
    @Mapping(target = "status", source = "status.code")
    AccountView toView(Account account);
}
