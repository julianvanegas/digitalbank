package co.edu.udea.certificacion.digitalbank.questions;

import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

/** Estado del cliente que viene en la última respuesta (registro o consulta por documento). */
public class EstadoDelCliente implements Question<String> {

    public static EstadoDelCliente enLaRespuesta() {
        return new EstadoDelCliente();
    }

    @Override
    public String answeredBy(Actor actor) {
        return SerenityRest.lastResponse().jsonPath().getString("status");
    }
}
