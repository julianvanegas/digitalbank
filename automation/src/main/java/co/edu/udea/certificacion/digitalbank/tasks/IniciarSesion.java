package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Post;
import co.edu.udea.certificacion.digitalbank.utils.Json;
import io.restassured.response.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/** HU02: el usuario envía sus credenciales. Si son válidas, el sistema le pide el segundo factor. */
public class IniciarSesion implements Task {

    private final String correo;
    private final String contrasena;

    public IniciarSesion(String correo, String contrasena) {
        this.correo = correo;
        this.contrasena = contrasena;
    }

    public static IniciarSesion con(String correo, String contrasena) {
        return Tasks.instrumented(IniciarSesion.class, correo, contrasena);
    }

    @Override
    @Step("{0} inicia sesión con sus credenciales")
    public <T extends Actor> void performAs(T actor) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("email", correo);
        cuerpo.put("password", contrasena);
        actor.attemptsTo(Post.sinSesion("/api/v1/auth/login", Json.objeto(cuerpo)));

        Response respuesta = SerenityRest.lastResponse();
        if (respuesta.statusCode() == 200) {
            actor.remember("correoSesion", correo);
            actor.remember("challengeId", respuesta.jsonPath().getString("challengeId"));
        }
    }
}
