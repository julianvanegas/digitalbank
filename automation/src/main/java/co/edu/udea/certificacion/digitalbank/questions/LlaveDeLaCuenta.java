package co.edu.udea.certificacion.digitalbank.questions;

import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

public class LlaveDeLaCuenta implements Question<String> {

    public static LlaveDeLaCuenta generada() {
        return new LlaveDeLaCuenta();
    }

    @Override
    public String answeredBy(Actor actor) {
        return actor.recall("ultimaLlaveCuenta");
    }
}