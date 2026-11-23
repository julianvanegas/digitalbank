package co.edu.udea.certificacion.digitalbank.runners;

import io.cucumber.junit.CucumberOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import org.junit.runner.RunWith;

/** Ejecuta las features de identidad y acceso (HU01, HU02 y HU08). */
@RunWith(CucumberWithSerenity.class)
@CucumberOptions(
        features = "src/test/resources/features/identidad",
        glue = "co.edu.udea.certificacion.digitalbank.stepdefinitions",
        tags = "@HU01 or @HU02 or @HU08",
        plugin = {"pretty"})
public class IdentidadYAccesoRunner {
}
