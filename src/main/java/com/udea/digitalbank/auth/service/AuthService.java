package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.PurposeCode;
import com.udea.digitalbank.auth.domain.StatusCode;
import com.udea.digitalbank.auth.domain.AuthSession;
import com.udea.digitalbank.auth.domain.UserAccount;
import com.udea.digitalbank.auth.dto.AuthResponse;
import com.udea.digitalbank.auth.dto.LoginRequest;
import com.udea.digitalbank.auth.dto.LoginResponse;
import com.udea.digitalbank.auth.repository.AuthSessionRepository;
import com.udea.digitalbank.auth.repository.UserAccountRepository;
import com.udea.digitalbank.auth.security.JwtUtil;
import com.udea.digitalbank.shared.exception.AccountBlockedException;
import com.udea.digitalbank.shared.exception.InvalidCredentialsException;
import com.udea.digitalbank.shared.exception.InvalidVerificationCodeException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Service
public class AuthService {

    private final UserAccountRepository accountRepository;
    private final AuthSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationService verificationService;
    private final VerificationMailer mailer;
    private final AccountStatusService accountStatusService;
    private final Catalogs catalogs;
    private final JwtUtil jwtUtil;

    public AuthService(UserAccountRepository accountRepository,
                       AuthSessionRepository sessionRepository,
                       PasswordEncoder passwordEncoder,
                       VerificationService verificationService,
                       VerificationMailer mailer,
                       AccountStatusService accountStatusService,
                       Catalogs catalogs,
                       JwtUtil jwtUtil) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationService = verificationService;
        this.mailer = mailer;
        this.accountStatusService = accountStatusService;
        this.catalogs = catalogs;
        this.jwtUtil = jwtUtil;
    }

    // noRollbackFor: el fallo de contraseña debe persistir el contador (y el bloqueo) aunque se lance la excepción
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountBlockedException.class})
    public LoginResponse login(LoginRequest request) {
        UserAccount account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Email o contraseña incorrectos"));

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            // Solo cuentas ACTIVE acumulan intentos: el resto ya no puede iniciar sesión
            if (account.getStatus().is(StatusCode.ACTIVE)) {
                accountStatusService.registerFailedAttempt(account);
            }
            throw new InvalidCredentialsException("Email o contraseña incorrectos");
        }

        // El estado se revela solo con la contraseña correcta
        switch (StatusCode.valueOf(account.getStatus().getCode())) {
            case ACTIVE -> { }
            case BLOCKED -> throw new AccountBlockedException(
                    "La cuenta está bloqueada, contacta a un administrador");
            case PENDING_VERIFICATION -> throw new AccountBlockedException(
                    "Debes confirmar tu correo electrónico antes de iniciar sesión");
            default -> throw new AccountBlockedException("La cuenta no está habilitada para iniciar sesión");
        }

        account.setFailedAttempts(0);
        accountRepository.save(account);

        IssuedChallenge challenge = verificationService.issue(account, PurposeCode.LOGIN);
        mailer.send(account.getEmail(), PurposeCode.LOGIN, challenge);

        // El JWT todavía no se emite: falta la verificación 2FA
        return new LoginResponse(true, challenge.challengeId());
    }

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public AuthResponse verifyTwoFactor(UUID challengeId, String code) {
        Long accountId = verificationService.verifyById(challengeId, PurposeCode.LOGIN, code);
        UserAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new InvalidVerificationCodeException("Código de verificación inválido o expirado"));

        // La cuenta pudo bloquearse entre el login y la verificación
        if (!account.getStatus().is(StatusCode.ACTIVE)) {
            throw new AccountBlockedException("La cuenta no está habilitada para iniciar sesión");
        }

        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = issuedAt.plus(Duration.ofMillis(jwtUtil.getExpirationMs()));
        account.setLastLoginAt(issuedAt);
        accountRepository.save(account);

        // Sesión única: sobrescribe la fila y con ello invalida el jti anterior
        String jti = jwtUtil.generateJti();
        sessionRepository.save(new AuthSession(accountId, jti, issuedAt, expiresAt));

        String token = jwtUtil.generateToken(accountId, account.getRole().getCode(), jti,
                toDate(issuedAt), toDate(expiresAt));
        return new AuthResponse(token);
    }

    @Transactional
    public void logout(Long accountId) {
        sessionRepository.deleteByAccount(accountId);
    }

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public void verifyEmail(String email, String code) {
        Long accountId = verificationService.verifyByEmail(email, PurposeCode.EMAIL_CONFIRMATION, code);
        UserAccount account = accountRepository.findById(accountId).orElseThrow();
        // Solo confirma cuentas pendientes; no reactiva una cuenta bloqueada o inactiva
        if (account.getStatus().is(StatusCode.PENDING_VERIFICATION)) {
            accountStatusService.changeStatus(accountId, StatusCode.ACTIVE);
        }
    }

    // Responde igual exista o no la cuenta, para no revelar qué emails están registrados
    @Transactional
    public void resendVerification(String email) {
        accountRepository.findByEmail(email)
                .filter(a -> a.getStatus().is(StatusCode.PENDING_VERIFICATION))
                .ifPresent(a -> verificationService.issueThrottled(a, PurposeCode.EMAIL_CONFIRMATION)
                        .ifPresent(challenge -> mailer.send(a.getEmail(), PurposeCode.EMAIL_CONFIRMATION, challenge)));
    }

    @Transactional
    public void requestPasswordReset(String email) {
        accountRepository.findByEmail(email)
                .ifPresent(a -> verificationService.issueThrottled(a, PurposeCode.PASSWORD_RESET)
                        .ifPresent(challenge -> mailer.send(a.getEmail(), PurposeCode.PASSWORD_RESET, challenge)));
    }

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public void resetPassword(String email, String code, String newPassword) {
        Long accountId = verificationService.verifyByEmail(email, PurposeCode.PASSWORD_RESET, code);
        UserAccount account = accountRepository.findById(accountId).orElseThrow();
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setPasswordChangedAt(LocalDateTime.now());
        account.setFailedAttempts(0);
        accountRepository.save(account);
        accountStatusService.revokeSession(accountId);
    }

    private static Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
}
