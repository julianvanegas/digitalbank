package com.udea.digitalbank.identity.service;

import com.udea.digitalbank.identity.domain.Customer;
import com.udea.digitalbank.identity.domain.CustomerStatus;
import com.udea.digitalbank.identity.domain.Role;
import com.udea.digitalbank.identity.dto.CustomerResponse;
import com.udea.digitalbank.identity.dto.RegistrationRequest;
import com.udea.digitalbank.identity.mapper.CustomerMapper;
import com.udea.digitalbank.identity.repository.CustomerRepository;
import com.udea.digitalbank.shared.exception.CustomerNotFoundException;
import com.udea.digitalbank.shared.exception.DuplicateCustomerException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    private static final int MINIMUM_AGE = 18;
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int INACTIVITY_MONTHS = 12;

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerMapper customerMapper;

    public CustomerService(CustomerRepository customerRepository,
                           PasswordEncoder passwordEncoder,
                           CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.customerMapper = customerMapper;
    }

    @Transactional
    public CustomerResponse register(RegistrationRequest request) {
        // Unicidad: ni el documento ni el email se pueden repetir
        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateCustomerException("Ya existe un cliente registrado con ese email");
        }
        if (customerRepository.existsByDocumentNumber(request.getDocumentNumber())) {
            throw new DuplicateCustomerException("Ya existe un cliente registrado con ese número de documento");
        }

        // Segmentación por edad: el cliente debe ser mayor de 18 años
        int age = Period.between(request.getBirthDate(), LocalDate.now()).getYears();
        if (age < MINIMUM_AGE) {
            throw new IllegalArgumentException("El cliente debe ser mayor de " + MINIMUM_AGE + " años");
        }

        Customer customer = new Customer();
        customer.setName(request.getName());
        customer.setDocumentType(request.getDocumentType());
        customer.setDocumentNumber(request.getDocumentNumber());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        customer.setBirthDate(request.getBirthDate());
        customer.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        customer.setRole(Role.USER);
        customer.setStatus(CustomerStatus.ACTIVE); // cambia a PENDING_VALIDATION si implementas KYC
        customer.setFailedAttempts(0);
        customer.setTwoFactorFailedAttempts(0);
        customer.setLastLogin(LocalDateTime.now());

        Customer saved = customerRepository.save(customer);
        return customerMapper.toResponse(saved);
    }

    public CustomerResponse getProfile(Long customerId) {
        return customerMapper.toResponse(findByIdOrThrow(customerId));
    }

    @Transactional
    public CustomerResponse updateProfile(Long customerId, String name, String phone, String email) {
        Customer customer = findByIdOrThrow(customerId);
        // solo campos de perfil editables — nunca role, status ni documentNumber desde aquí
        if (name != null && !name.isBlank()) {
            customer.setName(name);
        }
        if (phone != null && !phone.isBlank()) {
            customer.setPhone(phone);
        }
        if (email != null && !email.isBlank() && !email.equalsIgnoreCase(customer.getEmail())) {
            if (customerRepository.existsByEmailAndIdNot(email, customerId)) {
                throw new DuplicateCustomerException("Ya existe un cliente registrado con ese email");
            }
            customer.setEmail(email);
        }
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll().stream()
                .map(customerMapper::toResponse)
                .collect(Collectors.toList());
    }

    // Solo se debe llamar desde un endpoint protegido con @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void changeStatus(Long customerId, CustomerStatus newStatus) {
        Customer customer = findByIdOrThrow(customerId);
        customer.setStatus(newStatus);
        customerRepository.save(customer);
    }

    @Transactional
    public void incrementFailedAttempts(Customer customer) {
        customer.setFailedAttempts(customer.getFailedAttempts() + 1);
        if (customer.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            customer.setStatus(CustomerStatus.BLOCKED);
        }
        customerRepository.save(customer);
    }

    @Transactional
    public void resetFailedAttempts(Customer customer) {
        customer.setFailedAttempts(0);
        customerRepository.save(customer);
    }

    @Transactional
    @Scheduled(cron = "0 0 2 * * *")
    public void markInactiveCustomers() {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(INACTIVITY_MONTHS);
        customerRepository.findByStatusAndLastLoginBefore(CustomerStatus.ACTIVE, cutoff)
                .forEach(customer -> {
                    customer.setStatus(CustomerStatus.INACTIVE);
                    customer.setCurrentTokenId(null);
                    customerRepository.save(customer);
                });
    }

    private Customer findByIdOrThrow(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Cliente no encontrado: " + customerId));
    }
}
