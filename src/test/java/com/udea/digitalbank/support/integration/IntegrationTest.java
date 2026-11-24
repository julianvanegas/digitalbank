package com.udea.digitalbank.support.integration;

import com.udea.digitalbank.shared.utils.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;

/**
 * Base de las pruebas de integración de flujos completos: la aplicación entera (seguridad, servicios, listeners y
 * JPA) contra PostgreSQL real, atendida por MockMvc. Solo se sustituyen dos piezas:
 * <ul>
 *   <li>{@link EmailService}: no se llama a Resend; la prueba captura el texto del correo para leer los códigos.</li>
 *   <li>El {@link Clock}: es un {@link MutableClock} que la prueba adelanta para simular vencimientos.</li>
 * </ul>
 * Las peticiones confirman sus transacciones de verdad (no hay rollback), así que después de cada prueba se vacían
 * las tablas de datos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
public abstract class IntegrationTest {

    protected static final MutableClock CLOCK = new MutableClock();

    @TestBean(name = "clock", methodName = "com.udea.digitalbank.support.integration.IntegrationTest#testClock")
    protected Clock clock;

    @MockitoBean
    protected EmailService emailService;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    static Clock testClock() {
        return CLOCK;
    }

    @BeforeEach
    void resetClock() {
        CLOCK.reset();
    }

    @AfterEach
    void cleanDatabase() {
        new DatabaseCleaner(jdbc).clean();
    }
}
