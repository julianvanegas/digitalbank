package co.edu.udea.certificacion.digitalbank.tasks;

import net.serenitybdd.annotations.Step;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/**
 * Tarea compuesta "transferencia autorizada": solicita la transferencia (HU20) y la confirma con el código
 * recibido (HU21). Si el backend rechaza la solicitud, no intenta confirmar; el rechazo queda como última respuesta.
 */
public class TransferirConDobleFactor implements Task {

    private final String origen;
    private final String destino;
    private final long monto;

    public TransferirConDobleFactor(String origen, String destino, long monto) {
        this.origen = origen;
        this.destino = destino;
        this.monto = monto;
    }

    public static TransferirConDobleFactor desde(String origen, String destino, long monto) {
        return Tasks.instrumented(TransferirConDobleFactor.class, origen, destino, monto);
    }

    @Override
    @Step("{0} transfiere #monto pesos de la cuenta #origen a la cuenta #destino y lo autoriza con el segundo factor")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(TransferirFondos.desde(origen, destino, monto));
        if (SerenityRest.lastResponse().statusCode() == 201) {
            actor.attemptsTo(ConfirmarTransaccion.conElCodigoRecibido());
        }
    }
}
