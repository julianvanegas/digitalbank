package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.shared.exception.auth.InvalidVerificationCodeException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Confirmación del email de un usuario recién creado. */
@Service
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final VerificationService verificationService;
    private final VerificationDispatcher verificationDispatcher;
    private final UserStatusService userStatusService;

    public EmailVerificationService(UserRepository userRepository,
                                    VerificationService verificationService,
                                    VerificationDispatcher verificationDispatcher,
                                    UserStatusService userStatusService) {
        this.userRepository = userRepository;
        this.verificationService = verificationService;
        this.verificationDispatcher = verificationDispatcher;
        this.userStatusService = userStatusService;
    }

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public void verifyEmail(String email, String code) {
        Long userId = verificationService.verifyByEmail(email, PurposeEnum.EMAIL_CONFIRMATION, code);
        User user = userRepository.findById(userId).orElseThrow();
        // Solo confirma usuarios pendientes; no reactiva un usuario bloqueado o inactivo
        if (user.getStatus().is(UserStatusEnum.PENDING_VERIFICATION)) {
            userStatusService.changeStatus(userId, UserStatusEnum.ACTIVE);
        }
    }

    // Responde igual exista o no el usuario, para no revelar qué emails están registrados
    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmail(email)
                .filter(a -> a.getStatus().is(UserStatusEnum.PENDING_VERIFICATION))
                .ifPresent(a -> verificationDispatcher.issueAndSendThrottled(a, PurposeEnum.EMAIL_CONFIRMATION));
    }
}
