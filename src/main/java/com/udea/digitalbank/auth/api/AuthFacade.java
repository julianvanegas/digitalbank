package com.udea.digitalbank.auth.api;

import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.auth.service.UserCreatedEvent;
import com.udea.digitalbank.auth.service.UserStatusService;
import com.udea.digitalbank.auth.service.Catalogs;
import com.udea.digitalbank.shared.exception.auth.UserNotFoundException;
import com.udea.digitalbank.shared.exception.auth.DuplicateUserException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Única puerta de entrada de otros módulos a auth.
 * Los módulos solo guardan el id del usuario; nunca importan entidades ni repositorios de auth.
 */
@Service
public class AuthFacade {

    private final UserRepository userRepository;
    private final UserStatusService userStatusService;
    private final Catalogs catalogs;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public AuthFacade(UserRepository userRepository,
                      UserStatusService userStatusService,
                      Catalogs catalogs,
                      PasswordEncoder passwordEncoder,
                      ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.userStatusService = userStatusService;
        this.catalogs = catalogs;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Participa en la transacción del llamante: si el perfil falla, el usuario se revierte.
     * Deja al usuario en PENDING_VERIFICATION y, al confirmarse la transacción,
     * se emite el reto EMAIL_CONFIRMATION.
     */
    @Transactional
    public UserView createUser(String email, String password, RoleEnum roleEnum) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateUserException("Ya existe un usuario registrado con ese email");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPasswordChangedAt(now);
        user.setRole(catalogs.role(roleEnum));
        user.setStatus(catalogs.status(UserStatusEnum.PENDING_VERIFICATION));
        user.setStatusChangedAt(now);
        user.setCreatedAt(now);
        User saved = userRepository.save(user);

        eventPublisher.publishEvent(new UserCreatedEvent(saved.getId()));
        return toView(saved);
    }

    @Transactional
    public void changeStatus(Long userId, UserStatusEnum statusEnum) {
        userStatusService.changeStatus(userId, statusEnum);
    }

    @Transactional(readOnly = true)
    public UserView getUser(Long userId) {
        return userRepository.findById(userId)
                .map(AuthFacade::toView)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));
    }

    @Transactional(readOnly = true)
    public List<UserView> getUsers(Collection<Long> userIds) {
        return userRepository.findAllById(userIds).stream().map(AuthFacade::toView).toList();
    }

    private static UserView toView(User user) {
        return new UserView(user.getId(), user.getEmail(),
                user.getRole().getCode(), user.getStatus().getCode());
    }
}
