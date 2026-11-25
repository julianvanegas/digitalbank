package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Get;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/** HU12: el actor consulta el saldo de una cuenta con su token. El resultado se verifica con preguntas. */
public class ConsultarSaldo implements Task {

    private final String cuenta;

    public ConsultarSaldo(String cuenta) {
        this.cuenta = cuenta;
    }

    public static ConsultarSaldo deLaCuenta(String cuenta) {
        return Tasks.instrumented(ConsultarSaldo.class, cuenta);
    }

    @Override
    @Step("{0} consulta el saldo de la cuenta #cuenta")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(Get.de("/api/v1/accounts/" + cuenta + "/balance", actor.recall("token")));
    }
}
