package com.udea.digitalbank.auth.api;

import java.util.UUID;

// Vista de solo lectura de un usuario para otros módulos (sin hash ni datos de seguridad)
public record UserView(UUID id, String email, String role, String status) {
}
