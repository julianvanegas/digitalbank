package co.edu.udea.certificacion.digitalbank.runners;

import io.cucumber.junit.CucumberOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import org.junit.runner.RunWith;

/** Ejecuta las features de transacciones: HU19 (retiros), HU20 (transferencias) y el flujo E2E. */
@RunWith(CucumberWithSerenity.class)
@CucumberOptions(
        features = "src/test/resources/features/transacciones",
        glue = "co.edu.udea.certificacion.digitalbank.stepdefinitions",
        tags = "@HU19 or @HU20 or @E2E",
        plugin = {"pretty"})
public class TransaccionesRunner {
}
