package com.udea.digitalbank.shared.security;

import com.udea.digitalbank.support.integration.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static com.udea.digitalbank.support.HttpTestSupport.basicAuth;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las métricas con la aplicación completa: el endpoint real de Actuator publica en formato Prometheus, solo entra
 * quien presenta las credenciales de métricas, y el resto de Actuator sigue como antes (solo la salud es pública).
 * Las credenciales son las de application-test.properties.
 */
@AutoConfigureMetrics // @SpringBootTest desactiva por defecto los exportadores de métricas, Prometheus incluido
@DisplayName("Endpoint de métricas")
class MetricsEndpointIT extends IntegrationTest {

    private static final String PATH = "/actuator/prometheus";
    private static final String USER = "metrics-test";
    private static final String PASSWORD = "integration-test-metrics-password";

    @Test
    @DisplayName("Sin credenciales responde 401")
    void withoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Con credenciales incorrectas responde 401")
    void wrongCredentialsAreUnauthorized() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", basicAuth(USER, "incorrecta"))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Con las credenciales correctas publica las métricas en formato Prometheus, etiquetadas por aplicación")
    void validCredentialsReturnPrometheusMetrics() throws Exception {
        String body = mockMvc.perform(get(PATH).header("Authorization", basicAuth(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("jvm_memory_used_bytes")
                .contains("application=\"digitalbank\"");
    }

    @Test
    @DisplayName("La salud sigue siendo pública")
    void healthIsStillPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Los demás endpoints de Actuator siguen cerrados, aun con las credenciales de métricas")
    void otherActuatorEndpointsStayClosed() throws Exception {
        mockMvc.perform(get("/actuator/info").header("Authorization", basicAuth(USER, PASSWORD)))
                .andExpect(status().is4xxClientError());
    }
}
