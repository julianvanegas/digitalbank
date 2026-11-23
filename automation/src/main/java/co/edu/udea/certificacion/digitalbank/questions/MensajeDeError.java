package co.edu.udea.certificacion.digitalbank.questions;

import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

/** Mensaje de error de la última respuesta del servidor. */
public class MensajeDeError implements Question<String> {

    public static MensajeDeError delServidor() {
        return new MensajeDeError();
    }

    @Override
    public String answeredBy(Actor actor) {
        return SerenityRest.lastResponse().jsonPath().getString("message");
    }
}
