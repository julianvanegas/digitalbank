package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.RoleEnum;
import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.domain.ChallengePurpose;
import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.Role;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.domain.UserStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Datos de prueba compartidos por las pruebas de flujo del módulo auth. Los catálogos son entidades
 * inmutables sin setters, así que se construyen por reflexión solo para preparar los datos.
 */
final class AuthFixtures {

    private AuthFixtures() {
    }

    /** Propósito con la vigencia y el número de intentos que define la migración V1/V5. */
    static ChallengePurpose purpose(PurposeEnum purposeEnum) {
        int ttl = purposeEnum == PurposeEnum.TRANSACTION ? 3 : 5;
        ChallengePurpose purpose = new ChallengePurpose();
        ReflectionTestUtils.setField(purpose, "id", (short) (purposeEnum.ordinal() + 1));
        ReflectionTestUtils.setField(purpose, "purpose", purposeEnum.name());
        ReflectionTestUtils.setField(purpose, "ttlMinutes", ttl);
        ReflectionTestUtils.setField(purpose, "maxAttempts", 3);
        return purpose;
    }

    static UserStatus status(UserStatusEnum statusEnum) {
        UserStatus status = new UserStatus();
        ReflectionTestUtils.setField(status, "id", (short) (statusEnum.ordinal() + 1));
        ReflectionTestUtils.setField(status, "code", statusEnum.name());
        return status;
    }

    static Role role(RoleEnum roleEnum) {
        Role role = new Role();
        ReflectionTestUtils.setField(role, "id", (short) (roleEnum.ordinal() + 1));
        ReflectionTestUtils.setField(role, "code", roleEnum.name());
        return role;
    }

    /** Cliente con el correo, el hash de contraseña y el estado indicados, sin intentos fallidos. */
    static User customer(String email, String passwordHash, UserStatusEnum statusEnum) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setRole(role(RoleEnum.CUSTOMER));
        user.setStatus(status(statusEnum));
        user.setStatusChangedAt(LocalDateTime.now());
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }
}
