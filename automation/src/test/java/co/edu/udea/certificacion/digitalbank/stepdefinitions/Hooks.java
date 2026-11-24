package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import co.edu.udea.certificacion.digitalbank.utils.ConfiguracionQa;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import net.serenitybdd.screenplay.actors.Cast;
import net.serenitybdd.screenplay.actors.OnStage;
import net.serenitybdd.screenplay.rest.abilities.CallAnApi;

public class Hooks {

    /** Todos los actores pueden llamar a la API del banco. */
    @Before
    public void prepararEscenario() {
        OnStage.setTheStage(Cast.whereEveryoneCan(CallAnApi.at(ConfiguracionQa.urlBase())));
    }

    @After
    public void cerrarEscenario() {
        OnStage.drawTheCurtain();
    }
}
