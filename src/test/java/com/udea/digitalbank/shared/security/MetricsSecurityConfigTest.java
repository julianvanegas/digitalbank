package com.udea.digitalbank.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.udea.digitalbank.support.HttpTestSupport.basicAuth;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La cadena de seguridad de /actuator/prometheus, aislada de la API: solo entra quien presenta las credenciales Basic
 * configuradas. El endpoint real de Actuator no existe en este corte, así que un controller mínimo hace de endpoint;
 * lo que se prueba es la seguridad, no las métricas (eso lo cubre MetricsEndpointIT con la aplicación completa).
 */
// El corte no escanea nada más de la aplicación (el filtro JWT, los manejadores de errores...): solo importa esta cadena
@WebMvcTest(controllers = MetricsSecurityConfigTest.StubMetricsController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.udea\\.digitalbank\\..*"))
@Import({MetricsSecurityConfig.class, MetricsSecurityConfigTest.EnableSecurity.class,
        MetricsSecurityConfigTest.StubMetricsController.class})
@TestPropertySource(properties = {
        "metrics.security.user=scraper",
        "metrics.security.password=a-long-random-test-password"
})
@DisplayName("Seguridad de /actuator/prometheus")
class MetricsSecurityConfigTest {

    private static final String PATH = MetricsSecurityConfig.METRICS_PATH;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Sin credenciales responde 401 y pide autenticación Basic")
    void withoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Basic")));
    }

    @Test
    @DisplayName("Con la contraseña equivocada responde 401")
    void wrongPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", basicAuth("scraper", "otra-contrasena")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Con el usuario equivocado responde 401")
    void wrongUserIsUnauthorized() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", basicAuth("otro", "a-long-random-test-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Con las credenciales correctas responde 200")
    void validCredentialsAreAccepted() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", basicAuth("scraper", "a-long-random-test-password")))
                .andExpect(status().isOk())
                .andExpect(content().string("# stub"));
    }

    @Test
    @DisplayName("Un token Bearer de la API no sirve para leer las métricas")
    void bearerTokenIsNotAccepted() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", "Bearer un.token.cualquiera"))
                .andExpect(status().isUnauthorized());
    }

    /** Equivale a lo que activa SecurityConfig en la aplicación; aquí se importa solo la cadena de métricas. */
    @EnableWebSecurity
    static class EnableSecurity {
    }

    @RestController
    static class StubMetricsController {

        @GetMapping(MetricsSecurityConfig.METRICS_PATH)
        String metrics() {
            return "# stub";
        }
    }
}
