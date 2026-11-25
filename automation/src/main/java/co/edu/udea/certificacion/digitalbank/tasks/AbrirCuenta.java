package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Post;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/**
 * HU10: el cliente autenticado abre una cuenta de ahorros.
 *
 * La apertura del cliente no requiere cuerpo en la petición.
 * Cuando la creación es exitosa, se conservan en la memoria del actor
 * los datos de la cuenta para reutilizarlos en otros escenarios y en el E2E.
 */
public class AbrirCuenta implements Task {

    private static final String ENDPOINT = "/api/v1/accounts";

    public static AbrirCuenta deAhorros() {
        return Tasks.instrumented(AbrirCuenta.class);
    }

    @Override
    @Step("{0} abre una cuenta de ahorros")
    public <T extends Actor> void performAs(T actor) {

        actor.attemptsTo(
                Post.en(ENDPOINT, "", actor.recall("token"))
        );

        if (SerenityRest.lastResponse().statusCode() == 201) {

            String numeroCuenta =
                    SerenityRest.lastResponse()
                            .jsonPath()
                            .getString("accountNumber");

            String llaveCuenta =
                    SerenityRest.lastResponse()
                            .jsonPath()
                            .getString("accountKey");

            String estadoCuenta =
                    SerenityRest.lastResponse()
                            .jsonPath()
                            .getString("status");

            actor.remember("ultimaCuenta", numeroCuenta);
            actor.remember("ultimaLlaveCuenta", llaveCuenta);
            actor.remember("ultimoEstadoCuenta", estadoCuenta);
        }
    }
}