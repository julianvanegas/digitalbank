package com.udea.digitalbank.auth.api;

// Vista de solo lectura de una cuenta para otros módulos (sin hash ni datos de seguridad)
public record AccountView(Long id, String email, String role, String status) {
}
