package com.udea.digitalbank.identity.service;

import com.udea.digitalbank.identity.domain.Customer;
import com.udea.digitalbank.identity.repository.CustomerRepository;
import com.udea.digitalbank.shared.exception.InvalidTwoFactorCodeException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class TwoFactorService {

    private static final int CODE_LENGTH = 6;
    private static final int EXPIRATION_MINUTES = 5;
    private static final int MAX_FAILED_ATTEMPTS = 3;

    private final CustomerRepository customerRepository;
    private final JavaMailSender mailSender;
    private final SecureRandom random = new SecureRandom();

    public TwoFactorService(CustomerRepository customerRepository, JavaMailSender mailSender) {
        this.customerRepository = customerRepository;
        this.mailSender = mailSender;
    }

    @Transactional
    public void generateAndSendCode(Customer customer) {
        String code = generateNumericCode();

        customer.setTwoFactorCode(code);
        customer.setTwoFactorCodeExpiration(LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES));
        customer.setTwoFactorFailedAttempts(0);
        customerRepository.save(customer);

        sendEmail(customer.getEmail(), code);
    }

    @Transactional
    public void verifyCode(Customer customer, String code) {
        boolean matches = code != null && code.equals(customer.getTwoFactorCode());
        boolean expired = customer.getTwoFactorCodeExpiration() == null
                || customer.getTwoFactorCodeExpiration().isBefore(LocalDateTime.now());

        if (!matches || expired) {
            customer.setTwoFactorFailedAttempts(customer.getTwoFactorFailedAttempts() + 1);
            if (customer.getTwoFactorFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
                customer.setTwoFactorCode(null);
                customer.setTwoFactorCodeExpiration(null);
            }
            customerRepository.save(customer);
            throw new InvalidTwoFactorCodeException("Código de verificación inválido o expirado");
        }

        // Uso único: se invalida apenas se verifica correctamente
        customer.setTwoFactorCode(null);
        customer.setTwoFactorCodeExpiration(null);
        customer.setTwoFactorFailedAttempts(0);
        customerRepository.save(customer);
    }

    private String generateNumericCode() {
        int bound = (int) Math.pow(10, CODE_LENGTH);
        int code = random.nextInt(bound);
        return String.format("%0" + CODE_LENGTH + "d", code);
    }

    private void sendEmail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Tu código de verificación");
        message.setText("Tu código de verificación es: " + code + ". Expira en " + EXPIRATION_MINUTES + " minutos.");
        mailSender.send(message);
    }
}
