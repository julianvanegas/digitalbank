package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.dto.AuthResponse;
import com.udea.digitalbank.auth.dto.LoginRequest;
import com.udea.digitalbank.auth.dto.LoginResponse;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.shared.exception.auth.UserNotEnabledException;
import com.udea.digitalbank.shared.exception.auth.InvalidCredentialsException;
import com.udea.digitalbank.shared.exception.auth.InvalidVerificationCodeException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final VerificationService verificationService;
    private final VerificationDispatcher verificationDispatcher;
    private final UserStatusService userStatusService;

    public AuthService(UserRepository userRepository,
                       SessionService sessionService,
                       PasswordEncoder passwordEncoder,
                       VerificationService verificationService,
                       VerificationDispatcher verificationDispatcher,
                       UserStatusService userStatusService) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
        this.verificationService = verificationService;
        this.verificationDispatcher = verificationDispatcher;
        this.userStatusService = userStatusService;
    }

    // noRollbackFor: el fallo de contraseña debe persistir el contador (y el bloqueo) aunque se lance la excepción
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, UserNotEnabledException.class})
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Email o contraseña incorrectos"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // Solo usuarios ACTIVE acumulan intentos: el resto ya no puede iniciar sesión
            if (user.getStatus().is(UserStatusEnum.ACTIVE)) {
                userStatusService.registerFailedAttempt(user);
            }
            throw new InvalidCredentialsException("Email o contraseña incorrectos");
        }

        // El estado se revela solo con la contraseña correcta
        switch (UserStatusEnum.valueOf(user.getStatus().getCode())) {
            case ACTIVE -> { }
            case BLOCKED -> throw new UserNotEnabledException(
                    "El usuario está bloqueado, contacta a un administrador");
            case PENDING_VERIFICATION -> throw new UserNotEnabledException(
                    "Debes confirmar tu correo electrónico antes de iniciar sesión");
            default -> throw new UserNotEnabledException("El usuario no está habilitado para iniciar sesión");
        }

        user.setFailedAttempts(0);
        userRepository.save(user);

        IssuedChallenge challenge = verificationDispatcher.issueAndSend(user, PurposeEnum.LOGIN);

        // El JWT todavía no se emite: falta la verificación 2FA
        return new LoginResponse(true, challenge.challengeId());
    }

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public AuthResponse verifyTwoFactor(UUID challengeId, String code) {
        Long userId = verificationService.verifyById(challengeId, PurposeEnum.LOGIN, code);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidVerificationCodeException("Código de verificación inválido o expirado"));

        // El usuario pudo bloquearse entre el login y la verificación
        if (!user.getStatus().is(UserStatusEnum.ACTIVE)) {
            throw new UserNotEnabledException("El usuario no está habilitado para iniciar sesión");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Sesión única: abrir la nueva invalida el token anterior
        return new AuthResponse(sessionService.start(user));
    }

    @Transactional
    public void logout(Long userId) {
        sessionService.revoke(userId);
    }
}
