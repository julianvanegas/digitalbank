package com.udea.digitalbank.customer.service;

import com.udea.digitalbank.auth.api.RoleEnum;
import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.auth.api.UserApi;
import com.udea.digitalbank.customer.domain.Customer;
import com.udea.digitalbank.customer.domain.DocumentType;
import com.udea.digitalbank.customer.dto.CreateCustomerRequest;
import com.udea.digitalbank.customer.dto.CustomerResponse;
import com.udea.digitalbank.customer.dto.DocumentTypeResponse;
import com.udea.digitalbank.customer.mapper.CustomerMapper;
import com.udea.digitalbank.customer.repository.CustomerRepository;
import com.udea.digitalbank.customer.repository.DocumentTypeRepository;
import com.udea.digitalbank.shared.exception.InvalidRequestException;
import com.udea.digitalbank.shared.exception.customer.DuplicateCustomerException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomerService {

    private static final int MINIMUM_AGE = 18;

    private final CustomerRepository customerRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final UserApi userApi;
    private final CustomerMapper customerMapper;
    private final CustomerLookup customerLookup;

    public CustomerService(CustomerRepository customerRepository,
                           DocumentTypeRepository documentTypeRepository,
                           UserApi userApi,
                           CustomerMapper customerMapper,
                           CustomerLookup customerLookup) {
        this.customerRepository = customerRepository;
        this.documentTypeRepository = documentTypeRepository;
        this.userApi = userApi;
        this.customerMapper = customerMapper;
        this.customerLookup = customerLookup;
    }

    // Usuario y perfil en una sola transacción: si algo falla no quedan usuarios sin perfil ni al revés
    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        int age = Period.between(request.getBirthDate(), LocalDate.now()).getYears();
        if (age < MINIMUM_AGE) {
            throw new InvalidRequestException("El cliente debe ser mayor de " + MINIMUM_AGE + " años");
        }

        DocumentType documentType = documentTypeRepository.findById(request.getDocumentTypeId())
                .orElseThrow(() -> new InvalidRequestException(
                        "Tipo de documento inexistente: " + request.getDocumentTypeId()));

        if (customerRepository.existsByDocumentTypeIdAndDocumentNumber(documentType.getId(), request.getDocumentNumber())) {
            throw new DuplicateCustomerException("Ya existe un cliente registrado con ese número de documento");
        }

        // Valida el email único y deja al usuario en PENDING_VERIFICATION con una contraseña aleatoria;
        // el cliente define la suya al verificar su correo y queda PENDING_REVIEW hasta que un admin lo active
        UserView user = userApi.createUser(request.getEmail(), RoleEnum.CUSTOMER);

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

    // Catálogo para el formulario de registro: los ids que exige CreateCustomerRequest.documentTypeId
    @Transactional(readOnly = true)
    public List<DocumentTypeResponse> getDocumentTypes() {
        return documentTypeRepository.findAll(Sort.by("id")).stream()
                .map(customerMapper::toResponse)
                .toList();
    }

    // Perfil propio: se identifica por el id del usuario que viaja en el JWT
    @Transactional(readOnly = true)
    public CustomerResponse getProfile(UUID userId) {
        return toResponse(customerLookup.byUserId(userId));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(Short documentTypeId, String documentNumber) {
        return toResponse(customerLookup.byDocument(documentTypeId, documentNumber));
    }

    @Transactional
    public CustomerResponse updateProfile(UUID userId, String firstNames, String lastNames, String phone) {
        Customer customer = customerLookup.byUserId(userId);
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
        Map<UUID, UserView> users = customerLookup.usersOf(customers.getContent());
        return customers.map(c -> customerMapper.toResponse(c, users.get(c.getUserId())));
    }

    // Solo se debe llamar desde un endpoint que la seguridad reserve a administradores
    // auth valida que el estado exista en el catálogo user_status
    @Transactional
    public void changeStatus(Short documentTypeId, String documentNumber, UserStatusEnum statusEnum) {
        Customer customer = customerLookup.byDocument(documentTypeId, documentNumber);
        userApi.changeStatus(customer.getUserId(), statusEnum);
    }

    private CustomerResponse toResponse(Customer customer) {
        return customerMapper.toResponse(customer, customerLookup.userOf(customer));
    }
}
