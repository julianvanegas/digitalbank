package com.udea.digitalbank.support.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base de las pruebas de integración de repositorios: solo la capa JPA contra PostgreSQL real, con el esquema que
 * crean las migraciones de Flyway ({@code ddl-auto=validate} comprueba que las entidades coinciden). Cada prueba corre
 * en una transacción que se revierte al terminar, así que no deja datos.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
public abstract class RepositoryTest {

    @Autowired
    protected TestEntityManager em;

    /** Datos de prueba que se guardan con el {@link #em} de esta prueba. */
    protected TestData data;

    @BeforeEach
    void createTestData() {
        data = new TestData(em);
    }
}
