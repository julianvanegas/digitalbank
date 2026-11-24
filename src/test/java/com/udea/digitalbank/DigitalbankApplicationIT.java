package com.udea.digitalbank;

import com.udea.digitalbank.support.integration.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La aplicación completa arranca con el perfil de pruebas contra PostgreSQL real: Flyway migra, Hibernate valida el
 * esquema y Catalogs comprueba que los enums coinciden con los catálogos.
 */
@DisplayName("Arranque de la aplicación")
class DigitalbankApplicationIT extends IntegrationTest {

	@Test
	void contextLoads() {
		// Basta con que el contexto arranque
	}
}
