package com.udea.digitalbank.customer.service;

import com.udea.digitalbank.auth.api.RoleEnum;
import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.auth.api.AuthFacade;
import com.udea.digitalbank.customer.domain.Customer;
import com.udea.digitalbank.customer.domain.DocumentType;
import com.udea.digitalbank.customer.dto.CreateCustomerRequest;
import com.udea.digitalbank.customer.dto.CustomerResponse;
import com.udea.digitalbank.customer.mapper.CustomerMapper;
import com.udea.digitalbank.customer.repository.CustomerRepository;
import com.udea.digitalbank.customer.repository.DocumentTypeRepository;
import com.udea.digitalbank.shared.exception.customer.CustomerNotFoundException;
import com.udea.digitalbank.shared.exception.customer.DuplicateCustomerException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
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

    // Usuario y perfil en una sola transacción: si algo falla no quedan usuarios sin perfil ni al revés
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

        // Valida el email único, guarda el hash y deja al usuario en PENDING_VERIFICATION
        UserView user = authFacade.createUser(
                request.getEmail(), request.getPassword(), RoleEnum.CUSTOMER);

        Customer customer = new Customer();
        customer.setUserId(user.id());
        customer.setFirstNames(request.getFirstNames());
        customer.setLastNames(request.getLastNames());
        customer.setDocumentType(documentType);
        customer.setDocumentNumber(request.getDocumentNumber());
        customer.setPhone(request.getPhone());
        customer.setBirthDate(request.getBirthDate());

        return customerMapper.toResponse(customerRepository.save(customer), user);
    }

    // Perfil propio: se identifica por el id del usuario que viaja en el JWT
    @Transactional(readOnly = true)
    public CustomerResponse getProfile(Long userId) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomerNotFoundException("Cliente no encontrado para el usuario: " + userId));
        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(Long customerId) {
        return toResponse(findByIdOrThrow(customerId));
    }

    @Transactional
    public CustomerResponse updateProfile(Long userId, String firstNames, String lastNames, String phone) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomerNotFoundException("Cliente no encontrado para el usuario: " + userId));
        // solo campos de perfil editables — nunca documento, rol ni estado desde aquí
        if (firstNames != null) {
            customer.setFirstNames(firstNames);
        }
        if (lastNames != null) {
            customer.setLastNames(lastNames);
        }
        if (phone != null) {
            customer.setPhone(phone);
        }
        return toResponse(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> getAllCustomers(Pageable pageable) {
        Page<Customer> customers = customerRepository.findAll(pageable);
        Map<Long, UserView> users = authFacade
                .getUsers(customers.stream().map(Customer::getUserId).toList())
                .stream().collect(Collectors.toMap(UserView::id, Function.identity()));
        return customers.map(c -> customerMapper.toResponse(c, users.get(c.getUserId())));
    }

    // Solo se debe llamar desde un endpoint protegido con @PreAuthorize("hasRole('ADMIN')")
    // auth valida que el estado exista en el catálogo user_status
    @Transactional
    public void changeStatus(Long customerId, UserStatusEnum statusEnum) {
        Customer customer = findByIdOrThrow(customerId);
        authFacade.changeStatus(customer.getUserId(), statusEnum);
    }

    private CustomerResponse toResponse(Customer customer) {
        return customerMapper.toResponse(customer, authFacade.getUser(customer.getUserId()));
    }

    private Customer findByIdOrThrow(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Cliente no encontrado: " + customerId));
    }
}
