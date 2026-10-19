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

    /**
     * Qué roles pueden usar cada ruta. Es el único lugar donde se declaran los permisos por rol: los controllers no
     * llevan anotaciones de rol. Se comprueban en la cadena de seguridad, antes de que Spring resuelva los argumentos
     * del controller, así quien no tiene permiso recibe un 403 y nunca un 400 que revele las reglas de validación.
     * <p>
     * Todo lo que ninguna regla declara se deniega (SecurityConfig cierra con denyAll): olvidar una ruta la deja
     * cerrada, no abierta. El orden importa: gana la primera ruta que coincide, así que las más específicas van antes.
     */
    default List<RoleRoute> roleRoutes() {
        return List.of();
    }

    default void customize(HttpSecurity http) throws Exception {
    }

    /**
     * Ruta restringida por rol. Un {@code method} nulo equivale a cualquier método HTTP; el patrón admite {@code *} (un
     * segmento) y {@code **} (varios).
     * Sin roles (solo con {@link #anyAuthenticated}) basta con estar autenticado, sea cual sea el rol.
     */
    record RoleRoute(HttpMethod method, String pattern, List<String> roles) {

        public static RoleRoute get(String pattern, String... roles) {
            return of(HttpMethod.GET, pattern, roles);
        }

        public static RoleRoute post(String pattern, String... roles) {
            return of(HttpMethod.POST, pattern, roles);
        }

        public static RoleRoute put(String pattern, String... roles) {
            return of(HttpMethod.PUT, pattern, roles);
        }

        public static RoleRoute patch(String pattern, String... roles) {
            return of(HttpMethod.PATCH, pattern, roles);
        }

        public static RoleRoute any(String pattern, String... roles) {
            return of(null, pattern, roles);
        }

        /** Cualquier usuario autenticado, de cualquier rol (por ejemplo, cerrar sesión). */
        public static RoleRoute anyAuthenticated(HttpMethod method, String pattern) {
            return new RoleRoute(method, pattern, List.of());
        }

        // Una ruta de roles sin ningún rol sería, sin querer, una ruta abierta a todo usuario autenticado
        private static RoleRoute of(HttpMethod method, String pattern, String[] roles) {
            if (roles.length == 0) {
                throw new IllegalArgumentException("La ruta " + pattern + " necesita al menos un rol; "
                        + "para cualquier usuario autenticado use anyAuthenticated");
            }
            return new RoleRoute(method, pattern, List.of(roles));
        }
    }

    /** Ruta pública. Un {@code method} nulo equivale a cualquier método HTTP. */
    record PublicRoute(HttpMethod method, String pattern) {

        public static PublicRoute get(String pattern) {
            return new PublicRoute(HttpMethod.GET, pattern);
        }

        public static PublicRoute post(String pattern) {
            return new PublicRoute(HttpMethod.POST, pattern);
        }
    }
}
