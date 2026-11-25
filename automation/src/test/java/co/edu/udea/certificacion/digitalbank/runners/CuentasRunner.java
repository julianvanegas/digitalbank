package co.edu.udea.certificacion.digitalbank.runners;

import io.cucumber.junit.CucumberOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import org.junit.runner.RunWith;

@RunWith(CucumberWithSerenity.class)
@CucumberOptions(
        features = "src/test/resources/features/cuentas",
        glue = "co.edu.udea.certificacion.digitalbank.stepdefinitions",
        tags = "@HU10 or @HU12 or @HU18",
        plugin = {
                "pretty"
        }
)
public class CuentasRunner {
}