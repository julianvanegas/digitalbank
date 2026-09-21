package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.ChallengePurpose;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.domain.VerificationChallenge;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.auth.repository.VerificationChallengeRepository;
import com.udea.digitalbank.shared.exception.auth.InvalidVerificationCodeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Único mecanismo para validar la identidad en operaciones sensibles (2FA, confirmación de
 * email, recuperación de contraseña). La vigencia y los intentos salen de challenge_purpose.
 */
@Service
public class VerificationService {

    private static final int CODE_LENGTH = 6;
    private static final String INVALID_MESSAGE = "Código de verificación inválido o expirado";

    private final VerificationChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final Catalogs catalogs;
    private final CodeHasher codeHasher;
    private final long resendIntervalSeconds;
    private final SecureRandom random = new SecureRandom();

    public VerificationService(VerificationChallengeRepository challengeRepository,
                               UserRepository userRepository,
                               Catalogs catalogs,
                               CodeHasher codeHasher,
                               @Value("${verification.resend-interval-seconds}") long resendIntervalSeconds) {
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.catalogs = catalogs;
        this.codeHasher = codeHasher;
        this.resendIntervalSeconds = resendIntervalSeconds;
    }

    // Un reto por (usuario, propósito): pedir uno nuevo reemplaza el anterior
    @Transactional
    public IssuedChallenge issue(User user, PurposeEnum purposeEnum) {
        ChallengePurpose purpose = catalogs.purpose(purposeEnum);
        String code = generateNumericCode();

        challengeRepository.deleteByUserAndPurpose(user.getId(), purposeEnum.name());

        VerificationChallenge challenge = new VerificationChallenge();
        challenge.setId(UUID.randomUUID());
        challenge.setUserId(user.getId());
        challenge.setPurpose(purpose);
        challenge.setCodeHash(codeHasher.hash(user.getId(), purpose.getId(), code));
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(purpose.getTtlMinutes()));
        challengeRepository.save(challenge);

        return new IssuedChallenge(challenge.getId(), code, purpose.getTtlMinutes());
    }

    // Igual que issue, pero vacío si el último reto se emitió hace menos del intervalo mínimo
    @Transactional
    public Optional<IssuedChallenge> issueThrottled(User user, PurposeEnum purposeEnum) {
        Optional<VerificationChallenge> current =
                challengeRepository.lockByUserAndPurpose(user.getId(), purposeEnum.name());
        boolean tooSoon = current.isPresent()
                && current.get().issuedAt().plusSeconds(resendIntervalSeconds).isAfter(LocalDateTime.now());
        return tooSoon ? Optional.empty() : Optional.of(issue(user, purposeEnum));
    }

    // Flujo LOGIN: el cliente conoce el id del reto
    // noRollbackFor: el conteo de fallos y el borrado del reto deben persistir aunque se lance la excepción
    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public Long verifyById(UUID challengeId, PurposeEnum purposeEnum, String code) {
        VerificationChallenge challenge = challengeRepository.lockByIdAndPurpose(challengeId, purposeEnum.name())
                .orElseThrow(() -> new InvalidVerificationCodeException(INVALID_MESSAGE));
        return consume(challenge, code);
    }

    // Flujos que no revelan si un email existe: se resuelve por (email, propósito)
    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public Long verifyByEmail(String email, PurposeEnum purposeEnum, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidVerificationCodeException(INVALID_MESSAGE));
        VerificationChallenge challenge = challengeRepository
                .lockByUserAndPurpose(user.getId(), purposeEnum.name())
                .orElseThrow(() -> new InvalidVerificationCodeException(INVALID_MESSAGE));
        return consume(challenge, code);
    }

    @Transactional
    public void deleteExpired() {
        challengeRepository.deleteExpired(LocalDateTime.now());
    }

    private Long consume(VerificationChallenge challenge, String code) {
        if (challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            challengeRepository.delete(challenge);
            throw new InvalidVerificationCodeException(INVALID_MESSAGE);
        }

        ChallengePurpose purpose = challenge.getPurpose();
        boolean matches = code != null && codeHasher.matches(
                challenge.getUserId(), purpose.getId(), code, challenge.getCodeHash());

        if (!matches) {
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            if (challenge.getFailedAttempts() >= purpose.getMaxAttempts()) {
                challengeRepository.delete(challenge);
            } else {
                challengeRepository.save(challenge);
            }
            throw new InvalidVerificationCodeException(INVALID_MESSAGE);
        }

        // Uso único: se borra al verificarse
        challengeRepository.delete(challenge);
        return challenge.getUserId();
    }

    private String generateNumericCode() {
        return String.format("%0" + CODE_LENGTH + "d", random.nextInt((int) Math.pow(10, CODE_LENGTH)));
    }
}
