package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.integrations.CodigosVerificacion.Proposito;
import co.edu.udea.certificacion.digitalbank.interactions.Post;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeVerificacion;
import co.edu.udea.certificacion.digitalbank.utils.Json;
import io.restassured.response.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/**
 * Segundo paso del login: envía el código recibido y guarda el token de la sesión. Se usa a partir de un
 * {@link IniciarSesion} exitoso. La cubren los escenarios de HU03.
 */
public class ConfirmarSegundoFactor implements Task {

    private final String codigo;

    public ConfirmarSegundoFactor() {
        this.codigo = null;
    }

    public ConfirmarSegundoFactor(String codigo) {
        this.codigo = codigo;
    }

    public static ConfirmarSegundoFactor ahora() {
        return Tasks.instrumented(ConfirmarSegundoFactor.class);
    }

    public static ConfirmarSegundoFactor conCodigo(String codigo) {
        return Tasks.instrumented(ConfirmarSegundoFactor.class, codigo);
    }

    @Override
    @Step("{0} verifica el código del segundo factor")
    public <T extends Actor> void performAs(T actor) {
        String correo = actor.recall("correoSesion");
        String challengeId = actor.recall("challengeId");
        String codigoVerificacion = codigo == null
                ? actor.asksFor(CodigoDeVerificacion.enviadoA(correo, Proposito.LOGIN))
                : codigo;

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("challengeId", challengeId);
        cuerpo.put("code", codigoVerificacion);
        actor.attemptsTo(Post.sinSesion("/api/v1/auth/verify-2fa", Json.objeto(cuerpo)));

        Response respuesta = SerenityRest.lastResponse();
        if (respuesta.statusCode() == 200) {
            actor.remember("token", respuesta.jsonPath().getString("token"));
        }
    }
}
