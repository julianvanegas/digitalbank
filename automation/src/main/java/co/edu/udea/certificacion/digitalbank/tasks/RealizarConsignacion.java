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

public class RealizarConsignacion implements Task {

    private static final String ENDPOINT = "/api/v1/transactions/deposits";

    private final String cuenta;
    private final long monto;

    public RealizarConsignacion(String cuenta, long monto) {
        this.cuenta = cuenta;
        this.monto = monto;
    }

    public static RealizarConsignacion enLaCuenta(String cuenta, long monto) {
        return Tasks.instrumented(
                RealizarConsignacion.class,
                cuenta,
                monto
        );
    }

    @Override
    @Step("{0} consigna #monto pesos en la cuenta #cuenta")
    public <T extends Actor> void performAs(T actor) {

        Map<String, Object> cuerpo = new LinkedHashMap<>();

        cuerpo.put(
                "receiverAccountNumber",
                cuenta
        );

        cuerpo.put(
                "amount",
                monto
        );

        actor.attemptsTo(
                Post.en(
                        ENDPOINT,
                        Json.objeto(cuerpo),
                        actor.recall("token")
                )
        );

        if (SerenityRest.lastResponse().statusCode() == 201) {

            String referencia =
                    SerenityRest.lastResponse()
                            .jsonPath()
                            .getString("reference");

            String estado =
                    SerenityRest.lastResponse()
                            .jsonPath()
                            .getString("status");

            actor.remember(
                    "ultimaReferenciaConsignacion",
                    referencia
            );

            actor.remember(
                    "ultimoEstadoConsignacion",
                    estado
            );
        }
    }
}