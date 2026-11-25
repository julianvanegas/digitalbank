package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Post;
import co.edu.udea.certificacion.digitalbank.utils.Json;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;
import net.serenitybdd.rest.SerenityRest;

public class IniciarRetiro implements Task {

    private final String cuenta;
    private final BigDecimal monto;

    public IniciarRetiro(String cuenta, String monto) {
        this.cuenta = cuenta;
        this.monto = new BigDecimal(monto);
    }

    public static IniciarRetiro deCuenta(String cuenta, String monto) {
        return Tasks.instrumented(IniciarRetiro.class, cuenta, monto);
    }

    @Override
    @Step("{0} inicia un retiro de #monto")
    public <T extends Actor> void performAs(T actor) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("accountNumber", cuenta);
        cuerpo.put("amount", monto);
        actor.attemptsTo(Post.en("/api/v1/transactions/withdrawals", Json.objeto(cuerpo), actor.recall("token")));

        if (SerenityRest.lastResponse().statusCode() == 201) {
            actor.remember("referenciaTransaccion",
                    SerenityRest.lastResponse().jsonPath().getString("reference"));
        }
    }
}
