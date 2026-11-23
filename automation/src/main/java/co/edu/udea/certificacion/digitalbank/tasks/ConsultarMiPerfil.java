package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Get;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/** El cliente consulta su perfil con un token dado. Sirve para saber si una sesión sigue vigente. */
public class ConsultarMiPerfil implements Task {

    private final String token;

    public ConsultarMiPerfil(String token) {
        this.token = token;
    }

    public static ConsultarMiPerfil conToken(String token) {
        return Tasks.instrumented(ConsultarMiPerfil.class, token);
    }

    @Override
    @Step("{0} consulta su perfil con una sesión")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(Get.de("/api/v1/customers/profile", token));
    }
}
