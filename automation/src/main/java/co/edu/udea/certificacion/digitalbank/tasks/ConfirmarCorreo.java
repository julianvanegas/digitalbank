package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.integrations.CodigosVerificacion.Proposito;
import co.edu.udea.certificacion.digitalbank.interactions.Post;
import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeVerificacion;
import co.edu.udea.certificacion.digitalbank.utils.Json;
import java.util.LinkedHashMap;
import java.util.Map;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/** HU01: el cliente confirma su correo con el código recibido y define su contraseña. Pasa a pendiente de revisión. */
public class ConfirmarCorreo implements Task {

    private final String contrasena;

    public ConfirmarCorreo(String contrasena) {
        this.contrasena = contrasena;
    }

    public static ConfirmarCorreo definiendoLaContrasena(String contrasena) {
        return Tasks.instrumented(ConfirmarCorreo.class, contrasena);
    }

    @Override
    @Step("{0} confirma su correo y define su contraseña")
    public <T extends Actor> void performAs(T actor) {
        DatosCliente cliente = actor.recall("cliente");
        String codigo = actor.asksFor(CodigoDeVerificacion.enviadoA(cliente.correo(), Proposito.EMAIL_CONFIRMATION));
        actor.remember("contrasena", contrasena);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("email", cliente.correo());
        cuerpo.put("code", codigo);
        cuerpo.put("password", contrasena);
        cuerpo.put("confirmPassword", contrasena);
        actor.attemptsTo(Post.sinSesion("/api/v1/auth/verify-email", Json.objeto(cuerpo)));
    }
}
