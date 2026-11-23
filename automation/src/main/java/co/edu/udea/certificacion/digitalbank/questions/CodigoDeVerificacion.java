package co.edu.udea.certificacion.digitalbank.questions;

import co.edu.udea.certificacion.digitalbank.integrations.CodigosVerificacion;
import co.edu.udea.certificacion.digitalbank.integrations.CodigosVerificacion.Proposito;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

public class CodigoDeVerificacion implements Question<String> {

    private final String email;
    private final Proposito proposito;

    private CodigoDeVerificacion(String email, Proposito proposito) {
        this.email = email;
        this.proposito = proposito;
    }

    public static CodigoDeVerificacion enviadoA(String email, Proposito proposito) {
        return new CodigoDeVerificacion(email, proposito);
    }

    @Override
    public String answeredBy(Actor actor) {
        return CodigosVerificacion.obtener(email, proposito);
    }
}