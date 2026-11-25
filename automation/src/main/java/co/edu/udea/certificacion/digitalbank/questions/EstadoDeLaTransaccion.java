package co.edu.udea.certificacion.digitalbank.questions;

import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

/** Estado de la transacción en la última respuesta: PENDING, COMPLETED o REJECTED. */
public class EstadoDeLaTransaccion implements Question<String> {

    public static EstadoDeLaTransaccion enLaRespuesta() {
        return new EstadoDeLaTransaccion();
    }

    @Override
    public String answeredBy(Actor actor) {
        return SerenityRest.lastResponse().jsonPath().getString("status");
    }
}
