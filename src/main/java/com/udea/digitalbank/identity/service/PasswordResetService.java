package com.udea.digitalbank.identity.service;

import com.udea.digitalbank.identity.domain.Customer;
import com.udea.digitalbank.identity.repository.CustomerRepository;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final int TOKEN_EXPIRATION_MINUTES = 15;

    private final CustomerRepository customerRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(CustomerRepository customerRepository,
                                JavaMailSender mailSender,
                                PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void requestReset(String email) {
        customerRepository.findByEmail(email).ifPresent(this::createAndSendResetToken);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        Customer customer = customerRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token de recuperación inválido o expirado"));

        if (customer.getPasswordResetTokenExpiration() == null
                || customer.getPasswordResetTokenExpiration().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Token de recuperación inválido o expirado");
        }

        customer.setPasswordHash(passwordEncoder.encode(newPassword));
        customer.setPasswordResetToken(null);
        customer.setPasswordResetTokenExpiration(null);
        customer.setCurrentTokenId(null);
        customer.setFailedAttempts(0);
        customerRepository.save(customer);
    }

    private void createAndSendResetToken(Customer customer) {
        String token = UUID.randomUUID().toString();
        customer.setPasswordResetToken(token);
        customer.setPasswordResetTokenExpiration(
                LocalDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES));
        customerRepository.save(customer);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(customer.getEmail());
        message.setSubject("Recuperación de contraseña");
        message.setText("Tu código de recuperación es: " + token
                + ". Expira en " + TOKEN_EXPIRATION_MINUTES + " minutos.");
        mailSender.send(message);
    }
}
