package com.udea.digitalbank.customer.mapper;

import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.customer.domain.Customer;
import com.udea.digitalbank.customer.domain.DocumentType;
import com.udea.digitalbank.customer.dto.CustomerResponse;
import com.udea.digitalbank.customer.dto.DocumentTypeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    // Datos del perfil (Customer) más los del usuario (UserView, que llega por AuthFacade)
    @Mapping(target = "id", source = "customer.id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "role", source = "user.role")
    @Mapping(target = "status", source = "user.status")
    CustomerResponse toResponse(Customer customer, UserView user);

    DocumentTypeResponse toResponse(DocumentType documentType);
    // el mapeo CreateCustomerRequest -> Customer se realiza en el servicio: la contraseña la procesa auth
}
