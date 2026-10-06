package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.RoleEnum;
import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.shared.exception.auth.InvalidVerificationCodeException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/** Confirmación del email de un usuario recién creado, donde además define su contraseña. */
@Service
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final VerificationService verificationService;
    private final VerificationDispatcher verificationDispatcher;
    private final UserStatusService userStatusService;
    private final PasswordEncoder passwordEncoder;

    public EmailVerificationService(UserRepository userRepository,
                                    VerificationService verificationService,
                                    VerificationDispatcher verificationDispatcher,
                                    UserStatusService userStatusService,
                                    PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.verificationService = verificationService;
        this.verificationDispatcher = verificationDispatcher;
        this.userStatusService = userStatusService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Confirma el email y fija la contraseña elegida por la persona. Los clientes quedan PENDING_REVIEW
     * hasta que un administrador los active; los administradores quedan ACTIVE directamente.
     * La confirmación se compara antes de consumir el código, para no gastarlo por un error de tipeo.
     */
    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public void verifyEmail(String email, String code, String password, String confirmPassword) {
        if (password == null || !password.equals(confirmPassword)) {
            throw new IllegalArgumentException("La contraseña y su confirmación no coinciden");
        }

        UUID userId = verificationService.verifyByEmail(email, PurposeEnum.EMAIL_CONFIRMATION, code);
        User user = userRepository.findById(userId).orElseThrow();
        // Solo confirma usuarios pendientes: un reto viejo no debe cambiar la contraseña de una cuenta ya
        // habilitada, bloqueada o inactiva
        if (!user.getStatus().is(UserStatusEnum.PENDING_VERIFICATION)) {
            throw new InvalidVerificationCodeException("Código de verificación inválido o expirado");
        }

        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        UserStatusEnum next = user.getRole().is(RoleEnum.CUSTOMER)
                ? UserStatusEnum.PENDING_REVIEW
                : UserStatusEnum.ACTIVE;
        userStatusService.changeStatus(userId, next);
    }

    // Responde igual exista o no el usuario, para no revelar qué emails están registrados
    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmail(email)
                .filter(a -> a.getStatus().is(UserStatusEnum.PENDING_VERIFICATION))
                .ifPresent(a -> verificationDispatcher.issueAndSendThrottled(a, PurposeEnum.EMAIL_CONFIRMATION));
    }
}
