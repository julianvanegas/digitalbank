package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.shared.exception.auth.InvalidVerificationCodeException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Recuperación de contraseña: pedir el código y cambiarla con él. */
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationService verificationService;
    private final VerificationDispatcher verificationDispatcher;
    private final SessionService sessionService;

    public PasswordResetService(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                VerificationService verificationService,
                                VerificationDispatcher verificationDispatcher,
                                SessionService sessionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationService = verificationService;
        this.verificationDispatcher = verificationDispatcher;
        this.sessionService = sessionService;
    }

    // Responde igual exista o no el usuario, para no revelar qué emails están registrados
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email)
                .ifPresent(a -> verificationDispatcher.issueAndSendThrottled(a, PurposeEnum.PASSWORD_RESET));
    }

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public void resetPassword(String email, String code, String newPassword) {
        Long userId = verificationService.verifyByEmail(email, PurposeEnum.PASSWORD_RESET, code);
        User user = userRepository.findById(userId).orElseThrow();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setFailedAttempts(0);
        userRepository.save(user);
        sessionService.revoke(userId);
    }
}
