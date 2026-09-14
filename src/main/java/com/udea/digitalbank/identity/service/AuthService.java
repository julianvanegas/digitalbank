package com.udea.digitalbank.identity.service;

import com.udea.digitalbank.identity.domain.Customer;
import com.udea.digitalbank.identity.domain.CustomerStatus;
import com.udea.digitalbank.identity.dto.AuthResponse;
import com.udea.digitalbank.identity.dto.LoginRequest;
import com.udea.digitalbank.identity.dto.LoginResponse;
import com.udea.digitalbank.identity.repository.CustomerRepository;
import com.udea.digitalbank.shared.exception.AccountBlockedException;
import com.udea.digitalbank.shared.exception.CustomerNotFoundException;
import com.udea.digitalbank.shared.exception.InvalidCredentialsException;
import com.udea.digitalbank.shared.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerService customerService;
    private final TwoFactorService twoFactorService;
    private final JwtUtil jwtUtil;

    public AuthService(CustomerRepository customerRepository,
                       PasswordEncoder passwordEncoder,
                       CustomerService customerService,
                       TwoFactorService twoFactorService,
                       JwtUtil jwtUtil) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.customerService = customerService;
        this.twoFactorService = twoFactorService;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Customer customer = customerRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Email o contraseña incorrectos"));

        if (customer.getStatus() == CustomerStatus.BLOCKED) {
            throw new AccountBlockedException("La cuenta está bloqueada, contacta a un administrador");
        }
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new AccountBlockedException("El cliente no está habilitado para iniciar sesión");
        }

        if (!passwordEncoder.matches(request.getPassword(), customer.getPasswordHash())) {
            customerService.incrementFailedAttempts(customer);
            throw new InvalidCredentialsException("Email o contraseña incorrectos");
        }

        customerService.resetFailedAttempts(customer);
        twoFactorService.generateAndSendCode(customer);

        // El JWT todavía no se emite: falta la verificación 2FA
        return new LoginResponse(true, customer.getId());
    }

    @Transactional
    public AuthResponse verifyTwoFactor(Long customerId, String code) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Cliente no encontrado: " + customerId));

        twoFactorService.verifyCode(customer, code); // lanza excepción si el código es inválido o expiró
        customer.setLastLogin(LocalDateTime.now());

        String jti = jwtUtil.generateJti();
        customer.setCurrentTokenId(jti); // invalida cualquier sesión anterior (sesión única)
        customerRepository.save(customer);

        String token = jwtUtil.generateToken(customer.getId(), customer.getRole().name(), jti);
        return new AuthResponse(token);
    }

    @Transactional
    public void logout(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Cliente no encontrado: " + customerId));
        customer.setCurrentTokenId(null); // cualquier token existente deja de ser válido
        customerRepository.save(customer);
    }
}
