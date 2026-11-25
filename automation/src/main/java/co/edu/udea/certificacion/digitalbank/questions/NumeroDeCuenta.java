package co.edu.udea.certificacion.digitalbank.questions;

import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

public class NumeroDeCuenta implements Question<String> {

    public static NumeroDeCuenta generado() {
        return new NumeroDeCuenta();
    }

    @Override
    public String answeredBy(Actor actor) {
        return actor.recall("ultimaCuenta");
    }
}