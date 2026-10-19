package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.api.RoleEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.domain.UserStatus;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.shared.exception.auth.InvalidStatusTransitionException;
import com.udea.digitalbank.shared.exception.auth.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UserStatusService {

    static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int INACTIVITY_MONTHS = 12;
    // Tope de ids por sentencia, para no pasar de los parámetros que admite la base en una sola consulta
    private static final int INACTIVITY_BATCH_SIZE = 500;

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

    /**
     * Cambio de estado pedido por un administrador: solo las transiciones de {@link UserStatusTransitions}.
     * Pedir el estado actual no cambia nada. Es lo que usa UserApi; los flujos internos usan changeStatus.
     */
    @Transactional
    public void requestChange(UUID userId, UserStatusEnum target) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));
        UserStatusEnum current = UserStatusEnum.valueOf(user.getStatus().getCode());
        if (current == target) {
            return;
        }
        if (!UserStatusTransitions.isAllowed(current, target)) {
            throw new InvalidStatusTransitionException(
                    "No se puede cambiar el estado de un usuario de " + current + " a " + target);
        }
        applyStatus(user, target);
        userRepository.save(user);
    }

    /** Cambio interno sin matriz (p. ej. confirmar el correo). Los estados válidos son los del catálogo user_status. */
    @Transactional
    public void changeStatus(UUID userId, UserStatusEnum statusEnum) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));
        applyStatus(user, statusEnum);
        userRepository.save(user);
    }

    /** Suma un fallo y bloquea al llegar al máximo. */
    @Transactional
    public void registerFailedAttempt(User user) {
        user.setFailedAttempts((short) (user.getFailedAttempts() + 1));
        if (user.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            applyStatus(user, UserStatusEnum.BLOCKED);
        }
        userRepository.save(user);
    }

    /**
     * Pasa a INACTIVE a los clientes ACTIVE sin actividad en {@value #INACTIVITY_MONTHS} meses y cierra sus
     * sesiones. Los usuarios ADMIN quedan fuera por construcción. Por lotes y con sentencias UPDATE y DELETE:
     * no carga ni guarda los usuarios uno a uno.
     */
    @Transactional
    public void markInactiveCustomers() {
        LocalDateTime now = LocalDateTime.now();
        List<UUID> ids = userRepository.findIdleIds(
                RoleEnum.CUSTOMER.name(), UserStatusEnum.ACTIVE.name(), now.minusMonths(INACTIVITY_MONTHS));
        if (ids.isEmpty()) {
            return;
        }

        UserStatus active = catalogs.status(UserStatusEnum.ACTIVE);
        UserStatus inactive = catalogs.status(UserStatusEnum.INACTIVE);
        for (int from = 0; from < ids.size(); from += INACTIVITY_BATCH_SIZE) {
            List<UUID> batch = ids.subList(from, Math.min(from + INACTIVITY_BATCH_SIZE, ids.size()));
            userRepository.moveStatus(batch, active, inactive, now);
            sessionService.revokeAll(batch);
        }
    }

    private void applyStatus(User user, UserStatusEnum statusEnum) {
        UserStatus target = catalogs.status(statusEnum);
        if (!user.getStatus().getCode().equals(target.getCode())) {
            user.setStatus(target);
            user.setStatusChangedAt(LocalDateTime.now());
        }
        if (statusEnum == UserStatusEnum.ACTIVE) {
            user.setFailedAttempts((short) 0);
        } else {
            sessionService.revoke(user.getId());
        }
    }
}
