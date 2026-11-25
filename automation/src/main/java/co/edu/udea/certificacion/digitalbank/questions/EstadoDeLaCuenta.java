package co.edu.udea.certificacion.digitalbank.questions;

import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

public class EstadoDeLaCuenta implements Question<String> {

    public static EstadoDeLaCuenta actual() {
        return new EstadoDeLaCuenta();
    }

    @Override
    public String answeredBy(Actor actor) {
        return actor.recall("ultimoEstadoCuenta");
    }
}