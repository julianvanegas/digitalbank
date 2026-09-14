package com.udea.digitalbank.identity.mapper;

import com.udea.digitalbank.identity.domain.Customer;
import com.udea.digitalbank.identity.dto.CustomerResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerMapper {
    CustomerResponse toResponse(Customer customer);
    // el mapeo RegistrationRequest -> Customer mejor hazlo a mano en el service,
    // porque el password necesita pasar por el PasswordEncoder antes de asignarse
}
