package co.edu.udea.certificacion.digitalbank.questions;

import co.edu.udea.certificacion.digitalbank.interactions.Get;
import java.math.BigDecimal;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

public class SaldoDeLaCuenta implements Question<BigDecimal> {

    private final String numeroCuenta;

    public SaldoDeLaCuenta(String numeroCuenta) {
        this.numeroCuenta = numeroCuenta;
    }

    public static SaldoDeLaCuenta numero(String numeroCuenta) {
        return new SaldoDeLaCuenta(numeroCuenta);
    }

    @Override
    public BigDecimal answeredBy(Actor actor) {

        actor.attemptsTo(
                Get.de(
                        "/api/v1/accounts/" + numeroCuenta + "/balance",
                        actor.recall("token")
                )
        );

        return SerenityRest.lastResponse()
                .jsonPath()
                .getObject("balance", BigDecimal.class);
    }
}