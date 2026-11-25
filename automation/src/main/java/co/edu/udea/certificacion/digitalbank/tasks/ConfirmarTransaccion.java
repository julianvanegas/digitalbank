package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.integrations.CodigosVerificacion.Proposito;
import co.edu.udea.certificacion.digitalbank.interactions.Post;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeVerificacion;
import co.edu.udea.certificacion.digitalbank.utils.Json;
import java.util.Map;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/**
 * HU21: confirma la transacción pendiente (retiro o transferencia) que está en la memoria del actor
 * ({@code referenciaTransaccion}). El backend usa el mismo endpoint para los dos tipos.
 *
 * <p>{@link #conElCodigoRecibido()} lee de la base de QA el código enviado al correo del actor;
 * {@link #conElCodigo(String)} envía el código indicado, para probar códigos incorrectos.
 */
public class ConfirmarTransaccion implements Task {

    private final String codigo;

    public ConfirmarTransaccion(String codigo) {
        this.codigo = codigo;
    }

    /** Con el código que el backend envió al correo del titular. */
    public static ConfirmarTransaccion conElCodigoRecibido() {
        return Tasks.instrumented(ConfirmarTransaccion.class, "");
    }

    /** Con un código dado (por ejemplo, uno incorrecto). */
    public static ConfirmarTransaccion conElCodigo(String codigo) {
        return Tasks.instrumented(ConfirmarTransaccion.class, codigo);
    }

    @Override
    @Step("{0} confirma la transacción con el código de verificación")
    public <T extends Actor> void performAs(T actor) {
        String referencia = actor.recall("referenciaTransaccion");
        String codigoAEnviar = codigo.isBlank()
                ? actor.asksFor(CodigoDeVerificacion.enviadoA(actor.recall("correoSesion"), Proposito.TRANSACTION))
                : codigo;
        actor.attemptsTo(Post.en("/api/v1/transactions/" + referencia + "/confirm",
                Json.objeto(Map.of("code", codigoAEnviar)), actor.recall("token")));
    }
}
