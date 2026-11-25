package co.edu.udea.certificacion.digitalbank.questions.provisional;

import co.edu.udea.certificacion.digitalbank.interactions.Get;
import java.math.BigDecimal;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

/**
 * PROVISIONAL (HU12, de C): saldo actual de una cuenta, consultado con el token del actor. Hace su propia
 * petición, así que reemplaza la última respuesta: verifica el código y el mensaje de una operación antes de
 * preguntar el saldo. Cuando C integre su pregunta SaldoDeLaCuenta, se borra este paquete y se cambian los imports.
 */
public class SaldoDeLaCuenta implements Question<BigDecimal> {

    private final String cuenta;

    private SaldoDeLaCuenta(String cuenta) {
        this.cuenta = cuenta;
    }

    public static SaldoDeLaCuenta numero(String cuenta) {
        return new SaldoDeLaCuenta(cuenta);
    }

    @Override
    public BigDecimal answeredBy(Actor actor) {
        actor.attemptsTo(Get.de("/api/v1/accounts/" + cuenta + "/balance", actor.recall("token")));
        return new BigDecimal(SerenityRest.lastResponse().jsonPath().getString("balance"));
    }
}
