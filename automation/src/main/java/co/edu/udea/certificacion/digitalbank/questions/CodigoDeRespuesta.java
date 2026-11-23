package co.edu.udea.certificacion.digitalbank.questions;

import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

/** Código HTTP de la última respuesta del servidor. */
public class CodigoDeRespuesta implements Question<Integer> {

    public static CodigoDeRespuesta delServidor() {
        return new CodigoDeRespuesta();
    }

    @Override
    public Integer answeredBy(Actor actor) {
        return SerenityRest.lastResponse().statusCode();
    }
}
