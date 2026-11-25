package co.edu.udea.certificacion.digitalbank.runners;

import io.cucumber.junit.CucumberOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import org.junit.runner.RunWith;

/** Ejecuta escenarios de segundo factor, sesión y autorización de transacciones. */
@RunWith(CucumberWithSerenity.class)
@CucumberOptions(
        features = "src/test/resources/features/seguridad-transacciones",
        glue = "co.edu.udea.certificacion.digitalbank.stepdefinitions",
        tags = "@HU03 or @HU04 or @HU21",
        plugin = {"pretty"})
public class AutenticacionSesionTransaccionesRunner {
}
