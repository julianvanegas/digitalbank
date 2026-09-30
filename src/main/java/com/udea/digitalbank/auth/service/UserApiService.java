package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.UserApi;
import com.udea.digitalbank.auth.api.RoleEnum;
import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.shared.exception.auth.UserNotFoundException;
import com.udea.digitalbank.shared.exception.auth.DuplicateUserException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class UserApiService implements UserApi {

    private final UserRepository userRepository;
    private final UserStatusService userStatusService;
    private final Catalogs catalogs;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public UserApiService(UserRepository userRepository,
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

    @Override
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

    @Override
    @Transactional
    public void changeStatus(UUID userId, UserStatusEnum statusEnum) {
        userStatusService.changeStatus(userId, statusEnum);
    }

    @Override
    @Transactional(readOnly = true)
    public UserView getUser(UUID userId) {
        return userRepository.findById(userId)
                .map(UserApiService::toView)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserView> getUsers(Collection<UUID> userIds) {
        return userRepository.findAllById(userIds).stream().map(UserApiService::toView).toList();
    }

    private static UserView toView(User user) {
        return new UserView(user.getId(), user.getEmail(),
                user.getRole().getCode(), user.getStatus().getCode());
    }
}
