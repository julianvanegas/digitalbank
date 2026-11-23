package co.edu.udea.certificacion.digitalbank.questions;

import io.restassured.path.json.JsonPath;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

/**
 * Resultado del último inicio de sesión (HU02). Pregunta si las credenciales fueron aceptadas y se pide el segundo factor.
 */
public class ResultadoDelLogin implements Question<Boolean> {

    private ResultadoDelLogin() {
    }

    /** Verdadero si la última respuesta es 200, exige 2FA y trae el identificador del reto. */
    public static ResultadoDelLogin solicitaSegundoFactor() {
        return new ResultadoDelLogin();
    }

    @Override
    public Boolean answeredBy(Actor actor) {
        if (SerenityRest.lastResponse().statusCode() != 200) {
            return false;
        }
        JsonPath cuerpo = SerenityRest.lastResponse().jsonPath();
        String reto = cuerpo.getString("challengeId");
        return cuerpo.getBoolean("requiresTwoFactor") && reto != null && !reto.isBlank();
    }
}