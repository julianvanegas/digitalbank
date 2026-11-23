package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.interactions.Patch;
import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import co.edu.udea.certificacion.digitalbank.utils.Json;
import java.util.LinkedHashMap;
import java.util.Map;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/** HU08: cambia el estado de un cliente. Usa el token de quien lo ejecuta, que debe haber iniciado sesión. */
public class CambiarEstadoDelCliente implements Task {

    private final String estado;
    private final DatosCliente cliente;

    public CambiarEstadoDelCliente(String estado, DatosCliente cliente) {
        this.estado = estado;
        this.cliente = cliente;
    }

    public static CambiarEstadoDelCliente a(String estado, DatosCliente cliente) {
        return Tasks.instrumented(CambiarEstadoDelCliente.class, estado, cliente);
    }

    @Override
    @Step("{0} cambia el estado del cliente a #estado")
    public <T extends Actor> void performAs(T actor) {
        String token = actor.recall("token");
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("documentTypeId", cliente.tipoDocumento());
        cuerpo.put("documentNumber", cliente.numeroDocumento());
        cuerpo.put("status", estado);
        actor.attemptsTo(Patch.en("/api/v1/customers/status", Json.objeto(cuerpo), token));
    }
}
