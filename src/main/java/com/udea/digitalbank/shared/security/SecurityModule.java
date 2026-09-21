package com.udea.digitalbank.shared.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import java.util.List;

/**
 * Contrato con el que cada módulo aporta su parte de la seguridad sin que shared lo conozca:
 * sus rutas públicas y, si lo necesita, su mecanismo (filtros, política de sesión).
 * SecurityConfig recoge todas las implementaciones.
 */
public interface SecurityModule {

    default List<PublicRoute> publicRoutes() {
        return List.of();
    }

    default void customize(HttpSecurity http) throws Exception {
    }

    // method nulo = cualquier método HTTP
    record PublicRoute(HttpMethod method, String pattern) {

        public static PublicRoute post(String pattern) {
            return new PublicRoute(HttpMethod.POST, pattern);
        }
    }
}
