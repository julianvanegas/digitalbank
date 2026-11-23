package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Get;
import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/** El administrador consulta a un cliente por su documento. Requiere haber iniciado sesión. */
public class ConsultarCliente implements Task {

    private final DatosCliente cliente;

    public ConsultarCliente(DatosCliente cliente) {
        this.cliente = cliente;
    }

    public static ConsultarCliente conDocumento(DatosCliente cliente) {
        return Tasks.instrumented(ConsultarCliente.class, cliente);
    }

    @Override
    @Step("{0} consulta al cliente por su documento")
    public <T extends Actor> void performAs(T actor) {
        String token = actor.recall("token");
        String ruta = "/api/v1/customers/by-document?documentTypeId=" + cliente.tipoDocumento()
                + "&documentNumber=" + cliente.numeroDocumento();
        actor.attemptsTo(Get.de(ruta, token));
    }
}
