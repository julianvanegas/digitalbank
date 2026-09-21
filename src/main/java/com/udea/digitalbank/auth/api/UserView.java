package com.udea.digitalbank.auth.api;

// Vista de solo lectura de un usuario para otros módulos (sin hash ni datos de seguridad)
public record UserView(Long id, String email, String role, String status) {
}
