package co.edu.udea.certificacion.digitalbank.tasks.provisional;

import co.edu.udea.certificacion.digitalbank.interactions.Post;
import co.edu.udea.certificacion.digitalbank.utils.Json;
import java.util.LinkedHashMap;
import java.util.Map;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/**
 * PROVISIONAL (HU18, de C): el administrador consigna un monto en una cuenta. No pide segundo factor ni tiene topes.
 * Cuando C integre su tarea RealizarConsignacion, se borra este paquete y se cambian los imports.
 */
public class RealizarConsignacion implements Task {

    private final String cuenta;
    private final long monto;

    public RealizarConsignacion(String cuenta, long monto) {
        this.cuenta = cuenta;
        this.monto = monto;
    }

    public static RealizarConsignacion enLaCuenta(String cuenta, long monto) {
        return Tasks.instrumented(RealizarConsignacion.class, cuenta, monto);
    }

    @Override
    @Step("{0} consigna #monto pesos en la cuenta #cuenta")
    public <T extends Actor> void performAs(T actor) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("receiverAccountNumber", cuenta);
        cuerpo.put("amount", monto);
        actor.attemptsTo(Post.en("/api/v1/transactions/deposits", Json.objeto(cuerpo), actor.recall("token")));
    }
}
