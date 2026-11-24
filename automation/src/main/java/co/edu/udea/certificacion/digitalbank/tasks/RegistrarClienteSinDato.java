package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Post;
import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/** HU01: una persona intenta registrarse dejando fuera un dato obligatorio. */
public class RegistrarClienteSinDato implements Task {

    private final DatosCliente datos;
    private final String campo;

    public RegistrarClienteSinDato(DatosCliente datos, String campo) {
        this.datos = datos;
        this.campo = campo;
    }

    /** El campo se indica con su nombre en el JSON del registro (por ejemplo, "email"). */
    public static RegistrarClienteSinDato de(DatosCliente datos, String campo) {
        return Tasks.instrumented(RegistrarClienteSinDato.class, datos, campo);
    }

    @Override
    @Step("{0} intenta registrarse sin el dato #campo")
    public <T extends Actor> void performAs(T actor) {
        actor.remember("cliente", datos);
        actor.attemptsTo(Post.sinSesion("/api/v1/customers", datos.aJsonSin(campo)));
    }
}
