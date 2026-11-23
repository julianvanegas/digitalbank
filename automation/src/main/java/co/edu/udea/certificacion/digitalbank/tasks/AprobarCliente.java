package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/** HU08: el administrador aprueba a un cliente que está pendiente de revisión y lo deja activo. */
public class AprobarCliente implements Task {

    private final DatosCliente cliente;

    public AprobarCliente(DatosCliente cliente) {
        this.cliente = cliente;
    }

    public static AprobarCliente a(DatosCliente cliente) {
        return Tasks.instrumented(AprobarCliente.class, cliente);
    }

    @Override
    @Step("{0} aprueba al cliente")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(CambiarEstadoDelCliente.a("ACTIVE", cliente));
    }
}
