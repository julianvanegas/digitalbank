package com.udea.digitalbank.support.integration;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Deja la base como recién migrada: vacía las tablas de datos y conserva los catálogos que siembra Flyway (roles,
 * estados, tipos, propósitos de verificación y topes). Lo usan las pruebas que confirman transacciones de verdad, en
 * las que no hay rollback automático.
 */
public final class DatabaseCleaner {

    private static final String DATA_TABLES = String.join(", ",
            "transactions", "account_actions", "accounts", "customers", "employees",
            "verification_challenges", "auth_sessions", "users");

    private final JdbcTemplate jdbc;

    public DatabaseCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void clean() {
        jdbc.execute("TRUNCATE TABLE " + DATA_TABLES);
    }
}
