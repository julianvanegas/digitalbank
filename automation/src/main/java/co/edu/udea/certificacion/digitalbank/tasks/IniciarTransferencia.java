package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Post;
import co.edu.udea.certificacion.digitalbank.utils.Json;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

public class IniciarTransferencia implements Task {

    private final String origen;
    private final String destino;
    private final BigDecimal monto;

    public IniciarTransferencia(String origen, String destino, String monto) {
        this.origen = origen;
        this.destino = destino;
        this.monto = new BigDecimal(monto);
    }

    public static IniciarTransferencia entre(String origen, String destino, String monto) {
        return Tasks.instrumented(IniciarTransferencia.class, origen, destino, monto);
    }

    @Override
    @Step("{0} inicia una transferencia de #monto")
    public <T extends Actor> void performAs(T actor) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("senderAccountNumber", origen);
        cuerpo.put("receiverAccountNumber", destino);
        cuerpo.put("amount", monto);
        actor.attemptsTo(Post.en("/api/v1/transactions/transfers", Json.objeto(cuerpo), actor.recall("token")));

        if (SerenityRest.lastResponse().statusCode() == 201) {
            actor.remember("referenciaTransaccion",
                    SerenityRest.lastResponse().jsonPath().getString("reference"));
        }
    }
}
