package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Post;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/**
 * HU04: el actor cierra su sesión. Antes guarda el token que usaba como {@code tokenAnterior}, para que la
 * pregunta del escenario compruebe que el backend ya no lo acepta.
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
