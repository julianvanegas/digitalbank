package com.udea.digitalbank.customer.service;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.customer.api.CustomerApi;
import com.udea.digitalbank.customer.api.CustomerView;
import com.udea.digitalbank.customer.domain.Customer;
import com.udea.digitalbank.customer.mapper.CustomerMapper;
import com.udea.digitalbank.customer.repository.CustomerRepository;
import com.udea.digitalbank.shared.exception.customer.CustomerNotActiveException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomerApiService implements CustomerApi {

    private final CustomerRepository customerRepository;
    private final CustomerLookup customerLookup;
    private final CustomerMapper customerMapper;

    public CustomerApiService(CustomerRepository customerRepository,
                              CustomerLookup customerLookup,
                              CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.customerLookup = customerLookup;
        this.customerMapper = customerMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerView getCustomer(UUID customerId) {
        return toView(customerLookup.byId(customerId));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerView getCustomerByUserId(UUID userId) {
        return toView(customerLookup.byUserId(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerView> getCustomers(Collection<UUID> customerIds) {
        List<Customer> customers = customerRepository.findAllById(customerIds);
        Map<UUID, UserView> users = customerLookup.usersOf(customers);
        return customers.stream().map(c -> customerMapper.toView(c, users.get(c.getUserId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerView requireActiveCustomer(UUID customerId) {
        CustomerView view = getCustomer(customerId);
        if (!UserStatusEnum.ACTIVE.name().equals(view.status())) {
            throw new CustomerNotActiveException("El cliente no está activo: " + customerId);
        }
        return view;
    }

    private CustomerView toView(Customer customer) {
        return customerMapper.toView(customer, customerLookup.userOf(customer));
    }
}
