package co.edu.udea.certificacion.digitalbank.questions;

import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

/** Referencia (comprobante) de la transacción en la última respuesta. */
public class ComprobanteDeLaTransaccion implements Question<String> {

    public static ComprobanteDeLaTransaccion enLaRespuesta() {
        return new ComprobanteDeLaTransaccion();
    }

    @Override
    public String answeredBy(Actor actor) {
        return SerenityRest.lastResponse().jsonPath().getString("reference");
    }
}
