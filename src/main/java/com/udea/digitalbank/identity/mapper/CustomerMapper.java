package com.udea.digitalbank.identity.mapper;

import com.udea.digitalbank.identity.domain.Customer;
import com.udea.digitalbank.identity.dto.CustomerResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerMapper {
    CustomerResponse toResponse(Customer customer);
    // el mapeo RegistrationRequest -> Customer se realiza en el servicio por seguridad, porque el password necesita pasar por el PasswordEncoder antes de asignarse
}
