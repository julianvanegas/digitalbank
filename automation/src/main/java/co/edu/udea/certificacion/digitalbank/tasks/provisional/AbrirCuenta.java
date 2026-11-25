package co.edu.udea.certificacion.digitalbank.tasks.provisional;

import co.edu.udea.certificacion.digitalbank.interactions.Post;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/**
 * PROVISIONAL (HU10, de C): el cliente abre una cuenta de ahorros sin cuerpo en la petición. Guarda el número en
 * la memoria del actor ({@code ultimaCuenta}). Cuando C integre su tarea AbrirCuenta, se borra este paquete y se
 * cambian los imports.
 */
public class AbrirCuenta implements Task {

    public static AbrirCuenta deAhorros() {
        return Tasks.instrumented(AbrirCuenta.class);
    }

    @Override
    @Step("{0} abre una cuenta de ahorros")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(Post.en("/api/v1/accounts", "", actor.recall("token")));
        if (SerenityRest.lastResponse().statusCode() == 201) {
            actor.remember("ultimaCuenta", SerenityRest.lastResponse().jsonPath().getString("accountNumber"));
        }
    }
}
