package co.edu.udea.certificacion.digitalbank.questions;

import co.edu.udea.certificacion.digitalbank.tasks.ConsultarSaldo;
import co.edu.udea.certificacion.digitalbank.utils.RespuestaJson;
import java.math.BigDecimal;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

/**
 * HU12: saldo de una cuenta, como número exacto.
 *
 * <p>{@link #numero(String)} consulta el saldo con el token del actor, así que reemplaza la última respuesta:
 * verifica el código y el mensaje de una operación antes de preguntar el saldo. {@link #enLaRespuesta()} lee el
 * saldo de una consulta que ya se hizo con {@link ConsultarSaldo}.
 */
public class SaldoDeLaCuenta implements Question<BigDecimal> {

    private final String numeroCuenta;

    private SaldoDeLaCuenta(String numeroCuenta) {
        this.numeroCuenta = numeroCuenta;
    }

    /** Consulta el saldo actual de la cuenta indicada. */
    public static SaldoDeLaCuenta numero(String numeroCuenta) {
        return new SaldoDeLaCuenta(numeroCuenta);
    }

    /** Saldo de la última consulta de saldo, sin hacer otra petición. */
    public static SaldoDeLaCuenta enLaRespuesta() {
        return new SaldoDeLaCuenta(null);
    }

    @Override
    public BigDecimal answeredBy(Actor actor) {
        if (numeroCuenta != null) {
            actor.attemptsTo(ConsultarSaldo.deLaCuenta(numeroCuenta));
        }
        return RespuestaJson.decimal("balance");
    }
}
