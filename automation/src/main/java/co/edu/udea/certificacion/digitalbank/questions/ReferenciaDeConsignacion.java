package co.edu.udea.certificacion.digitalbank.questions;

import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

public class ReferenciaDeConsignacion implements Question<String> {

    public static ReferenciaDeConsignacion generada() {
        return new ReferenciaDeConsignacion();
    }

    @Override
    public String answeredBy(Actor actor) {
        return actor.recall("ultimaReferenciaConsignacion");
    }
}