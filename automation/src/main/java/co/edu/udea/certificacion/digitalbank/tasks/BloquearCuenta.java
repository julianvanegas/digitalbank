package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Patch;
import co.edu.udea.certificacion.digitalbank.utils.Json;
import java.util.Map;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/**
 * HU15: el administrador bloquea una cuenta. Solo el rol ADMIN puede hacerlo; usa el token de quien la ejecuta.
 * La usan HU19 CA07 y HU20 CA04 para preparar una cuenta no activa.
 */
public class BloquearCuenta implements Task {

    private static final String MOTIVO = "Bloqueo de prueba de QA";

    private final String cuenta;

    public BloquearCuenta(String cuenta) {
        this.cuenta = cuenta;
    }

    public static BloquearCuenta numero(String cuenta) {
        return Tasks.instrumented(BloquearCuenta.class, cuenta);
    }

    @Override
    @Step("{0} bloquea la cuenta #cuenta")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(Patch.en("/api/v1/accounts/" + cuenta + "/block",
                Json.objeto(Map.of("reason", MOTIVO)), actor.recall("token")));
    }
}
