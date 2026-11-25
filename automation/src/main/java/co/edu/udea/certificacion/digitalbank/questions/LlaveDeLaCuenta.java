package co.edu.udea.certificacion.digitalbank.questions;

import co.edu.udea.certificacion.digitalbank.utils.RespuestaJson;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

/** Llave (identificador visible) de la cuenta que viene en la última respuesta. */
public class LlaveDeLaCuenta implements Question<String> {

    public static LlaveDeLaCuenta generada() {
        return new LlaveDeLaCuenta();
    }

    @Override
    public String answeredBy(Actor actor) {
        return RespuestaJson.texto("accountKey");
    }
}
