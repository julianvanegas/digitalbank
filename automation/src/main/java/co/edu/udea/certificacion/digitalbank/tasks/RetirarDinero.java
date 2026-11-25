package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Post;
import co.edu.udea.certificacion.digitalbank.utils.Json;
import java.util.LinkedHashMap;
import java.util.Map;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/**
 * HU19: el cliente solicita un retiro. El backend lo deja pendiente y envía el código de segundo factor (HU21).
 * Si la solicitud se acepta (201), la referencia queda en la memoria del actor para confirmarla después.
 * Usa el token de quien la ejecuta.
 */
public class RetirarDinero implements Task {

    private final String cuenta;
    private final long monto;

    public RetirarDinero(String cuenta, long monto) {
        this.cuenta = cuenta;
        this.monto = monto;
    }

    public static RetirarDinero deLaCuenta(String cuenta, long monto) {
        return Tasks.instrumented(RetirarDinero.class, cuenta, monto);
    }

    @Override
    @Step("{0} solicita retirar #monto pesos de la cuenta #cuenta")
    public <T extends Actor> void performAs(T actor) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("accountNumber", cuenta);
        cuerpo.put("amount", monto);
        actor.attemptsTo(Post.en("/api/v1/transactions/withdrawals", Json.objeto(cuerpo), actor.recall("token")));
        if (SerenityRest.lastResponse().statusCode() == 201) {
            actor.remember("referencia", SerenityRest.lastResponse().jsonPath().getString("reference"));
        }
    }
}
