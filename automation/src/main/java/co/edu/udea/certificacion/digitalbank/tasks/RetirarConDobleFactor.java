package co.edu.udea.certificacion.digitalbank.tasks;

import net.serenitybdd.annotations.Step;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/**
 * Tarea compuesta "retiro autorizado": solicita el retiro (HU19) y lo confirma con el código recibido (HU21).
 * Si el backend rechaza la solicitud, no intenta confirmar; el rechazo queda como última respuesta.
 */
public class RetirarConDobleFactor implements Task {

    private final String cuenta;
    private final long monto;

    public RetirarConDobleFactor(String cuenta, long monto) {
        this.cuenta = cuenta;
        this.monto = monto;
    }

    public static RetirarConDobleFactor deLaCuenta(String cuenta, long monto) {
        return Tasks.instrumented(RetirarConDobleFactor.class, cuenta, monto);
    }

    @Override
    @Step("{0} retira #monto pesos de la cuenta #cuenta y lo autoriza con el segundo factor")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(RetirarDinero.deLaCuenta(cuenta, monto));
        if (SerenityRest.lastResponse().statusCode() == 201) {
            actor.attemptsTo(ConfirmarTransaccion.conElCodigoRecibido());
        }
    }
}
