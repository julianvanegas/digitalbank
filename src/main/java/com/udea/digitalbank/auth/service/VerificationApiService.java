package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.CodeCheckResult;
import com.udea.digitalbank.auth.api.VerificationApi;
import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.shared.exception.auth.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implementación de {@link VerificationApi}: traduce el contrato que {@code auth} ofrece a otros módulos a los
 * servicios internos de verificación, con el propósito {@code TRANSACTION}. No contiene reglas propias del código
 * (vigencia, intentos, uso único): esas viven en {@code VerificationService}.
 */
@Service
public class VerificationApiService implements VerificationApi {

    private final VerificationService verificationService;
    private final VerificationDispatcher verificationDispatcher;
    private final UserRepository userRepository;
    private final Catalogs catalogs;

    public VerificationApiService(VerificationService verificationService,
                                  VerificationDispatcher verificationDispatcher,
                                  UserRepository userRepository,
                                  Catalogs catalogs) {
        this.verificationService = verificationService;
        this.verificationDispatcher = verificationDispatcher;
        this.userRepository = userRepository;
        this.catalogs = catalogs;
    }

    @Override
    @Transactional
    public void sendTransactionCode(UUID userId, String detail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));
        verificationDispatcher.issueAndSend(user, PurposeEnum.TRANSACTION, detail);
    }

    @Override
    @Transactional
    public CodeCheckResult verifyTransactionCode(UUID userId, String code) {
        return verificationService.check(userId, PurposeEnum.TRANSACTION, code);
    }

    @Override
    public int transactionCodeTtlMinutes() {
        return catalogs.purpose(PurposeEnum.TRANSACTION).getTtlMinutes();
    }
}
