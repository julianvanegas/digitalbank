package com.udea.digitalbank.auth.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Única puerta de entrada de otros módulos a auth.
 * Los módulos solo guardan el id del usuario; nunca importan entidades ni repositorios de auth.
 * La implementación vive en auth.service (UserApiService).
 */
public interface UserApi {

    /**
     * Participa en la transacción del llamante: si el perfil falla, el usuario se revierte.
     * Deja al usuario en PENDING_VERIFICATION con una contraseña aleatoria que nadie conoce: la persona
     * define la suya al confirmar su correo (verify-email). Al confirmarse la transacción,
     * se emite el reto EMAIL_CONFIRMATION.
     */
    UserView createUser(String email, RoleEnum roleEnum);

    /**
     * Cambio de estado pedido por un administrador. Solo admite las transiciones permitidas (por ejemplo
     * ACTIVE a INACTIVE o BLOCKED, y BLOCKED a ACTIVE) y rechaza el resto con un conflicto; pedir el estado
     * que el usuario ya tiene no cambia nada. PENDING_VERIFICATION y PENDING_REVIEW nunca se fijan por aquí.
     */
    void changeStatus(UUID userId, UserStatusEnum statusEnum);

    /**
     * Inactivación por una regla del negocio (por ejemplo, el cliente cerró su última cuenta), no por un
     * administrador. Pasa el usuario de ACTIVE a INACTIVE y cierra su sesión; si no está ACTIVE (bloqueado o ya
     * inactivo) no cambia nada. Participa en la transacción del llamante.
     */
    void markInactive(UUID userId);

    UserView getUser(UUID userId);

    List<UserView> getUsers(Collection<UUID> userIds);
}
