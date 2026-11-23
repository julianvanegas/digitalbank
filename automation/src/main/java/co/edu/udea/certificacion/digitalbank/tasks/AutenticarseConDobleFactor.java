package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.utils.ConfiguracionQa;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/** Login completo: credenciales más segundo factor. Deja el token de la sesión en la memoria del actor. */
public class AutenticarseConDobleFactor implements Task {

    private final String correo;
    private final String contrasena;

    public AutenticarseConDobleFactor(String correo, String contrasena) {
        this.correo = correo;
        this.contrasena = contrasena;
    }

    public static AutenticarseConDobleFactor con(String correo, String contrasena) {
        return Tasks.instrumented(AutenticarseConDobleFactor.class, correo, contrasena);
    }

    public static AutenticarseConDobleFactor comoAdministrador() {
        return con(ConfiguracionQa.correoAdministrador(), ConfiguracionQa.contrasenaAdministrador());
    }

    @Override
    @Step("{0} inicia sesión y verifica el segundo factor")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(IniciarSesion.con(correo, contrasena), ConfirmarSegundoFactor.ahora());
    }
}
