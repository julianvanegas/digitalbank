package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Get;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/** El cliente lista sus cuentas con un token dado. Sirve para comprobar si una sesión sigue siendo válida. */
public class ConsultarMisCuentas implements Task {

    private final String token;

    public ConsultarMisCuentas(String token) {
        this.token = token;
    }

    public static ConsultarMisCuentas conToken(String token) {
        return Tasks.instrumented(ConsultarMisCuentas.class, token);
    }

    @Override
    @Step("{0} consulta sus cuentas con una sesión")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(Get.de("/api/v1/accounts", token));
    }
}
