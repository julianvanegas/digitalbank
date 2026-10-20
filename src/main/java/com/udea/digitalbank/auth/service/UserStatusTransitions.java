package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.UserStatusEnum;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Cambios de estado que un administrador puede pedir por la API. No aplica a los flujos internos (confirmar el
 * correo, bloquear por intentos fallidos, inactivar por inactividad), que mueven el estado por su propio camino.
 * <p>
 * PENDING_VERIFICATION y PENDING_REVIEW nunca se fijan desde aquí: el primero solo lo deja atrás la confirmación
 * del correo, y al segundo solo se llega al confirmarlo. Pedir el estado que el usuario ya tiene no es una
 * transición: se acepta sin cambios.
 */
final class UserStatusTransitions {

    private static final Map<UserStatusEnum, Set<UserStatusEnum>> ALLOWED = new EnumMap<>(UserStatusEnum.class);

    static {
        ALLOWED.put(UserStatusEnum.PENDING_VERIFICATION, EnumSet.noneOf(UserStatusEnum.class));
        ALLOWED.put(UserStatusEnum.PENDING_REVIEW, EnumSet.of(UserStatusEnum.ACTIVE, UserStatusEnum.BLOCKED));
        ALLOWED.put(UserStatusEnum.ACTIVE, EnumSet.of(UserStatusEnum.INACTIVE, UserStatusEnum.BLOCKED));
        ALLOWED.put(UserStatusEnum.INACTIVE, EnumSet.of(UserStatusEnum.ACTIVE, UserStatusEnum.BLOCKED));
        ALLOWED.put(UserStatusEnum.BLOCKED, EnumSet.of(UserStatusEnum.ACTIVE));
    }

    private UserStatusTransitions() {
    }

    static boolean isAllowed(UserStatusEnum from, UserStatusEnum to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
