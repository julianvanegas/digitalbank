package com.udea.digitalbank.customer.mapper;

import com.udea.digitalbank.auth.api.AccountView;
import com.udea.digitalbank.customer.domain.Customer;
import com.udea.digitalbank.customer.domain.DocumentType;
import com.udea.digitalbank.customer.dto.CustomerResponse;
import com.udea.digitalbank.customer.dto.DocumentTypeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    // Datos del perfil (Customer) más los de la cuenta (AccountView, que llega por AuthFacade)
    @Mapping(target = "id", source = "customer.id")
    @Mapping(target = "userAccountId", source = "account.id")
    @Mapping(target = "email", source = "account.email")
    @Mapping(target = "role", source = "account.role")
    @Mapping(target = "status", source = "account.status")
    CustomerResponse toResponse(Customer customer, AccountView account);

    DocumentTypeResponse toResponse(DocumentType documentType);
    // el mapeo CreateCustomerRequest -> Customer se realiza en el servicio: la contraseña la procesa auth
}
