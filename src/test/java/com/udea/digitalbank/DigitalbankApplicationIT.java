package com.udea.digitalbank;

import com.udea.digitalbank.shared.config.SchedulingConfig;
import com.udea.digitalbank.support.integration.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La aplicación completa arranca con el perfil de pruebas contra PostgreSQL real: Flyway migra, Hibernate valida el
 * esquema y Catalogs comprueba que los enums coinciden con los catálogos.
 */
@DisplayName("Arranque de la aplicación")
class DigitalbankApplicationIT extends IntegrationTest {

	@Autowired
	private ApplicationContext context;

	@Test
	@DisplayName("Arranca con las migraciones aplicadas y sin trabajos programados")
	void contextLoads() {
		assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where not success", Long.class))
				.as("migraciones fallidas").isZero();
		assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Long.class))
				.as("migraciones aplicadas").isPositive();
		// El perfil test apaga @EnableScheduling para que ningún trabajo cambie los datos en mitad de una prueba
		assertThat(context.getBeanNamesForType(SchedulingConfig.class)).isEmpty();
	}
}
