package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Post;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/**
 * HU10: el cliente autenticado abre una cuenta de ahorros; la petición no lleva cuerpo. Si se crea (201), el número
 * queda en la memoria del actor como {@code ultimaCuenta} para usarla en los pasos siguientes. Lo demás de la cuenta
 * (llave, estado) se verifica con preguntas sobre la respuesta.
 */
public class AbrirCuenta implements Task {

    private static final String ENDPOINT = "/api/v1/accounts";

    public static AbrirCuenta deAhorros() {
        return Tasks.instrumented(AbrirCuenta.class);
    }

    @Override
    @Step("{0} abre una cuenta de ahorros")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(Post.en(ENDPOINT, "", actor.recall("token")));
        if (SerenityRest.lastResponse().statusCode() == 201) {
            actor.remember("ultimaCuenta", SerenityRest.lastResponse().jsonPath().getString("accountNumber"));
        }
    }
}
