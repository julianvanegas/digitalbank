package co.edu.udea.certificacion.digitalbank.tasks.provisional;

import co.edu.udea.certificacion.digitalbank.interactions.Post;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/**
 * PROVISIONAL (HU04, de B): el actor cierra su sesión. Guarda el token que usaba como {@code tokenAnterior} para
 * comprobar después que el backend lo rechaza. Cuando B integre su tarea CerrarSesion, se borra esta clase y se
 * cambian los imports.
 */
public class CerrarSesion implements Task {

    public static CerrarSesion ahora() {
        return Tasks.instrumented(CerrarSesion.class);
    }

    @Override
    @Step("{0} cierra su sesión")
    public <T extends Actor> void performAs(T actor) {
        String token = actor.recall("token");
        actor.remember("tokenAnterior", token);
        actor.attemptsTo(Post.en("/api/v1/auth/logout", "", token));
    }
}
