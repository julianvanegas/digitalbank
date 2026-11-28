package co.edu.udea.certificacion.digitalbank.questions;

import co.edu.udea.certificacion.digitalbank.utils.RespuestaJson;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

/** Número de la cuenta que viene en la última respuesta (apertura o detalle de una cuenta). */
public class NumeroDeCuenta implements Question<String> {

    public static NumeroDeCuenta generado() {
        return new NumeroDeCuenta();
    }

    @Override
    public String answeredBy(Actor actor) {
        return RespuestaJson.texto("accountNumber");
    }
}
