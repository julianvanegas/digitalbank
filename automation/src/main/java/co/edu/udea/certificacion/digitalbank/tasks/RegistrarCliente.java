package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Post;
import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/** HU01: una persona se registra como cliente. Queda pendiente de verificar su correo. */
public class RegistrarCliente implements Task {

    private final DatosCliente datos;

    public RegistrarCliente(DatosCliente datos) {
        this.datos = datos;
    }

    public static RegistrarCliente con(DatosCliente datos) {
        return Tasks.instrumented(RegistrarCliente.class, datos);
    }

    @Override
    @Step("{0} se registra como cliente del banco")
    public <T extends Actor> void performAs(T actor) {
        actor.remember("cliente", datos);
        actor.attemptsTo(Post.sinSesion("/api/v1/customers", datos.aJson()));
    }
}
