package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Get;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

public class ConsultarTransaccion implements Task {

    public static ConsultarTransaccion actual() {
        return Tasks.instrumented(ConsultarTransaccion.class);
    }

    @Override
    @Step("{0} consulta el estado de la transacción")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(Get.de(
                "/api/v1/transactions/" + actor.recall("referenciaTransaccion"),
                actor.recall("token")));
    }
}
