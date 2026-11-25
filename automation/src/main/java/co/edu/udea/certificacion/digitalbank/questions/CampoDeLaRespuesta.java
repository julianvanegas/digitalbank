package co.edu.udea.certificacion.digitalbank.questions;

import co.edu.udea.certificacion.digitalbank.utils.RespuestaJson;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

/**
 * Un campo de texto de la última respuesta del servidor (por ejemplo, {@code email}, {@code type} o
 * {@code accountKey}). Sirve para validar el contenido de lo que devuelve la API, no solo que haya respondido.
 */
public class CampoDeLaRespuesta implements Question<String> {

    private final String campo;

    private CampoDeLaRespuesta(String campo) {
        this.campo = campo;
    }

    public static CampoDeLaRespuesta llamado(String campo) {
        return new CampoDeLaRespuesta(campo);
    }

    @Override
    public String answeredBy(Actor actor) {
        return RespuestaJson.texto(campo);
    }
}
