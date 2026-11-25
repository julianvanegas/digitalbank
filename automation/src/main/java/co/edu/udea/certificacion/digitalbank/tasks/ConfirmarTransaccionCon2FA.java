package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.integrations.CodigosVerificacion.Proposito;
import co.edu.udea.certificacion.digitalbank.interactions.Post;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeVerificacion;
import co.edu.udea.certificacion.digitalbank.utils.Json;
import java.util.Map;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

public class ConfirmarTransaccionCon2FA implements Task {

    private final String codigo;

    public ConfirmarTransaccionCon2FA(String codigo) {
        this.codigo = codigo;
    }

    public static ConfirmarTransaccionCon2FA conCodigo(String codigo) {
        return Tasks.instrumented(ConfirmarTransaccionCon2FA.class, codigo);
    }

    public static ConfirmarTransaccionCon2FA conCodigoDelBuzon() {
        return conCodigo(null);
    }

    @Override
    @Step("{0} confirma la transacción con su código de seguridad")
    public <T extends Actor> void performAs(T actor) {
        String codigoUsado = codigo == null
                ? actor.asksFor(CodigoDeVerificacion.enviadoA(
                        actor.recall("correoSesion"), Proposito.TRANSACTION))
                : codigo;
        String referencia = actor.recall("referenciaTransaccion");
        actor.attemptsTo(Post.en(
                "/api/v1/transactions/" + referencia + "/confirm",
                Json.objeto(Map.of("code", codigoUsado)),
                actor.recall("token")));
    }
}
