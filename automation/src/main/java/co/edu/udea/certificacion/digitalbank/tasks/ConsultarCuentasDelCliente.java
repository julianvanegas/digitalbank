package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Get;
import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/** El administrador lista las cuentas de un cliente a partir de su documento, en cualquier estado. */
public class ConsultarCuentasDelCliente implements Task {

    private final DatosCliente cliente;

    public ConsultarCuentasDelCliente(DatosCliente cliente) {
        this.cliente = cliente;
    }

    public static ConsultarCuentasDelCliente conDocumento(DatosCliente cliente) {
        return Tasks.instrumented(ConsultarCuentasDelCliente.class, cliente);
    }

    @Override
    @Step("{0} consulta las cuentas del cliente por su documento")
    public <T extends Actor> void performAs(T actor) {
        String ruta = "/api/v1/accounts/by-customer?documentTypeId=" + cliente.tipoDocumento()
                + "&documentNumber=" + cliente.numeroDocumento();
        actor.attemptsTo(Get.de(ruta, actor.recall("token")));
    }
}
