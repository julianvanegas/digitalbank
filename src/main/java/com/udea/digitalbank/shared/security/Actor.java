package com.udea.digitalbank.shared.security;

import java.util.UUID;

/**
 * Quién hace la petición: el id del usuario del token y si es administrador. Los controllers lo reciben ya
 * resuelto ({@code @CurrentActor}) en vez de leer Authentication y comparar roles a mano; el servicio decide con él qué
 * puede ver o hacer. Se construye siempre a partir del contexto de seguridad, nunca de un dato de la petición.
 */
public record Actor(UUID userId, boolean admin) {
}
