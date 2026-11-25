package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Post;
import co.edu.udea.certificacion.digitalbank.utils.Json;
import java.util.LinkedHashMap;
import java.util.Map;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/**
 * HU20: el cliente solicita una transferencia. El backend la deja pendiente y envía el código de segundo factor
 * (HU21). Si la solicitud se acepta (201), la referencia queda en la memoria del actor para confirmarla después.
 * Usa el token de quien la ejecuta.
 */
public class TransferirFondos implements Task {

    private final String origen;
    private final String destino;
    private final long monto;

    public TransferirFondos(String origen, String destino, long monto) {
        this.origen = origen;
        this.destino = destino;
        this.monto = monto;
    }

    public static TransferirFondos desde(String origen, String destino, long monto) {
        return Tasks.instrumented(TransferirFondos.class, origen, destino, monto);
    }

    @Override
    @Step("{0} solicita transferir #monto pesos de la cuenta #origen a la cuenta #destino")
    public <T extends Actor> void performAs(T actor) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("senderAccountNumber", origen);
        cuerpo.put("receiverAccountNumber", destino);
        cuerpo.put("amount", monto);
        actor.attemptsTo(Post.en("/api/v1/transactions/transfers", Json.objeto(cuerpo), actor.recall("token")));
        if (SerenityRest.lastResponse().statusCode() == 201) {
            actor.remember("referencia", SerenityRest.lastResponse().jsonPath().getString("reference"));
        }
    }
}
