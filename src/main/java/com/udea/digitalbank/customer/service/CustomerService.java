package com.udea.digitalbank.customer.service;

import com.udea.digitalbank.auth.domain.CategoryCode;
import com.udea.digitalbank.auth.domain.RoleCode;
import com.udea.digitalbank.auth.domain.StatusCode;
import com.udea.digitalbank.auth.api.AccountView;
import com.udea.digitalbank.auth.api.AuthFacade;
import com.udea.digitalbank.customer.domain.Customer;
import com.udea.digitalbank.customer.domain.DocumentType;
import com.udea.digitalbank.customer.dto.CreateCustomerRequest;
import com.udea.digitalbank.customer.dto.CustomerResponse;
import com.udea.digitalbank.customer.mapper.CustomerMapper;
import com.udea.digitalbank.customer.repository.CustomerRepository;
import com.udea.digitalbank.customer.repository.DocumentTypeRepository;
import com.udea.digitalbank.shared.exception.CustomerNotFoundException;
import com.udea.digitalbank.shared.exception.DuplicateCustomerException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    private static final int MINIMUM_AGE = 18;

    private final CustomerRepository customerRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final AuthFacade authFacade;
    private final CustomerMapper customerMapper;

    public CustomerService(CustomerRepository customerRepository,
                           DocumentTypeRepository documentTypeRepository,
                           AuthFacade authFacade,
                           CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.documentTypeRepository = documentTypeRepository;
        this.authFacade = authFacade;
        this.customerMapper = customerMapper;
    }

    // Cuenta y perfil en una sola transacción: si algo falla no quedan cuentas sin perfil ni al revés
    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        int age = Period.between(request.getBirthDate(), LocalDate.now()).getYears();
        if (age < MINIMUM_AGE) {
            throw new IllegalArgumentException("El cliente debe ser mayor de " + MINIMUM_AGE + " años");
        }

        DocumentType documentType = documentTypeRepository.findById(request.getDocumentTypeId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tipo de documento inexistente: " + request.getDocumentTypeId()));

        if (customerRepository.existsByDocumentNumber(request.getDocumentNumber())) {
            throw new DuplicateCustomerException("Ya existe un cliente registrado con ese número de documento");
        }

        // Valida el email único, guarda el hash y deja la cuenta en (CUSTOMER, PENDING_VERIFICATION)
        AccountView account = authFacade.createAccount(
                request.getEmail(), request.getPassword(), RoleCode.CUSTOMER, CategoryCode.CUSTOMER);

        Customer customer = new Customer();
        customer.setUserAccountId(account.id());
        customer.setName(request.getName());
        customer.setDocumentType(documentType);
        customer.setDocumentNumber(request.getDocumentNumber());
        customer.setPhone(request.getPhone());
        customer.setBirthDate(request.getBirthDate());

        return customerMapper.toResponse(customerRepository.save(customer), account);
    }

    // Perfil propio: se identifica por el id de la cuenta que viaja en el JWT
    @Transactional(readOnly = true)
    public CustomerResponse getProfile(Long userAccountId) {
        Customer customer = customerRepository.findByUserAccountId(userAccountId)
                .orElseThrow(() -> new CustomerNotFoundException("Cliente no encontrado para la cuenta: " + userAccountId));
        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(Long customerId) {
        return toResponse(findByIdOrThrow(customerId));
    }

    @Transactional
    public CustomerResponse updateProfile(Long userAccountId, String name, String phone) {
        Customer customer = customerRepository.findByUserAccountId(userAccountId)
                .orElseThrow(() -> new CustomerNotFoundException("Cliente no encontrado para la cuenta: " + userAccountId));
        // solo campos de perfil editables — nunca documento, rol ni estado desde aquí
        if (name != null && !name.isBlank()) {
            customer.setName(name);
        }
        if (phone != null && !phone.isBlank()) {
            customer.setPhone(phone);
        }
        return toResponse(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers() {
        List<Customer> customers = customerRepository.findAll();
        Map<Long, AccountView> accounts = authFacade
                .getAccounts(customers.stream().map(Customer::getUserAccountId).toList())
                .stream().collect(Collectors.toMap(AccountView::id, Function.identity()));
        return customers.stream()
                .map(c -> customerMapper.toResponse(c, accounts.get(c.getUserAccountId())))
                .toList();
    }

    // Solo se debe llamar desde un endpoint protegido con @PreAuthorize("hasRole('ADMIN')")
    // auth valida que el estado exista en la categoría CUSTOMER de la cuenta
    @Transactional
    public void changeStatus(Long customerId, StatusCode statusCode) {
        Customer customer = findByIdOrThrow(customerId);
        authFacade.changeStatus(customer.getUserAccountId(), statusCode);
    }

    private CustomerResponse toResponse(Customer customer) {
        return customerMapper.toResponse(customer, authFacade.getAccount(customer.getUserAccountId()));
    }

    private Customer findByIdOrThrow(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Cliente no encontrado: " + customerId));
    }
}
