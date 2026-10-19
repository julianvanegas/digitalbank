package com.udea.digitalbank.shared.security;

/**
 * Nombres de los roles tal como viajan en el token (sin el prefijo ROLE_). Los módulos los usan para declarar qué
 * rutas protege cada uno sin importar el catálogo de auth.
 */
public final class Roles {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String ADMIN = "ADMIN";

    private Roles() {
    }
}
