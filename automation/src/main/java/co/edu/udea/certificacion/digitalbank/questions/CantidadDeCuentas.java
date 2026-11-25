package co.edu.udea.certificacion.digitalbank.questions;

import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

/** Cuántas cuentas trae la última respuesta, que debe ser una lista (consulta de cuentas de un cliente). */
public class CantidadDeCuentas implements Question<Integer> {

    public static CantidadDeCuentas enLaRespuesta() {
        return new CantidadDeCuentas();
    }

    @Override
    public Integer answeredBy(Actor actor) {
        return SerenityRest.lastResponse().jsonPath().getList("$").size();
    }
}
