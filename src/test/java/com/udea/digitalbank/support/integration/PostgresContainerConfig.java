package com.udea.digitalbank.support.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL real para las pruebas de integración. {@code @ServiceConnection} hace que Spring use la URL, el usuario y
 * la contraseña del contenedor en lugar de las variables DB_*. El contenedor es único para toda la ejecución: los
 * contextos de {@code @DataJpaTest} y de {@code @SpringBootTest} lo comparten y Flyway migra una sola vez.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }
}
