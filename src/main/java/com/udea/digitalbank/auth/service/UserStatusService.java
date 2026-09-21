package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.api.RoleEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.domain.UserStatus;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.shared.exception.auth.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserStatusService {

    static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int INACTIVITY_MONTHS = 12;

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final Catalogs catalogs;

    public UserStatusService(UserRepository userRepository,
                             SessionService sessionService,
                             Catalogs catalogs) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.catalogs = catalogs;
    }

    // Los estados válidos son los del catálogo user_status; un valor fuera de él se rechaza
    @Transactional
    public void changeStatus(Long userId, UserStatusEnum statusEnum) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));
        applyStatus(user, statusEnum);
    }

    // Un usuario inexistente no está activo. Lo consulta el filtro JWT en cada petición autenticada
    public boolean isActive(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getStatus().is(UserStatusEnum.ACTIVE))
                .orElse(false);
    }

    // Suma un fallo y bloquea al llegar al máximo
    @Transactional
    public void registerFailedAttempt(User user) {
        user.setFailedAttempts(user.getFailedAttempts() + 1);
        if (user.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            applyStatus(user, UserStatusEnum.BLOCKED);
        }
        userRepository.save(user);
    }

    // Solo mueve clientes ACTIVE -> INACTIVE: los usuarios ADMIN quedan fuera por construcción
    @Transactional
    public void markInactiveCustomers() {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(INACTIVITY_MONTHS);
        userRepository.findIdle(RoleEnum.CUSTOMER.name(), UserStatusEnum.ACTIVE.name(), cutoff)
                .forEach(user -> {
                    applyStatus(user, UserStatusEnum.INACTIVE);
                    userRepository.save(user);
                });
    }

    private void applyStatus(User user, UserStatusEnum statusEnum) {
        UserStatus target = catalogs.status(statusEnum);
        if (!user.getStatus().getCode().equals(target.getCode())) {
            user.setStatus(target);
            user.setStatusChangedAt(LocalDateTime.now());
        }
        if (statusEnum == UserStatusEnum.ACTIVE) {
            user.setFailedAttempts(0);
        } else {
            sessionService.revoke(user.getId());
        }
        userRepository.save(user);
    }
}
