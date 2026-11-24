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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Si METRICS_USER o METRICS_PASSWORD no se definen, el endpoint queda cerrado y no abierto: ni siquiera pasa quien
 * presenta credenciales vacías, que coincidirían con la configuración vacía si la comparación fuera ingenua.
 */
// El corte no escanea nada más de la aplicación (el filtro JWT, los manejadores de errores...): solo importa esta cadena
@WebMvcTest(controllers = MetricsSecurityNotConfiguredTest.StubMetricsController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.udea\\.digitalbank\\..*"))
@Import({MetricsSecurityConfig.class, MetricsSecurityNotConfiguredTest.EnableSecurity.class,
        MetricsSecurityNotConfiguredTest.StubMetricsController.class})
@TestPropertySource(properties = {
        "metrics.security.user=",
        "metrics.security.password="
})
@DisplayName("Seguridad de /actuator/prometheus sin credenciales configuradas")
class MetricsSecurityNotConfiguredTest {

    private static final String PATH = MetricsSecurityConfig.METRICS_PATH;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Sin credenciales responde 401")
    void withoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Con credenciales vacías responde 401, no 200")
    void emptyCredentialsAreNotAccepted() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", basicAuth("", ""))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Con cualquier credencial responde 401")
    void anyCredentialsAreNotAccepted() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", basicAuth("scraper", "cualquier-cosa"))).andExpect(status().isUnauthorized());
    }

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
