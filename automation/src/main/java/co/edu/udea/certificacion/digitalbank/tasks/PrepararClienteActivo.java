package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/**
 * Tarea compuesta "cliente Activo listo para operar": el cliente se registra y confirma su correo
 * (queda pendiente de revisión) y un administrador lo aprueba (queda activo). Pensada para que
 * HU03 a HU21 y el E2E partan de un cliente que ya puede iniciar sesión y abrir cuentas.
 *
 * La ejecuta el actor Cliente; el administrador debe haber iniciado sesión (tiene su token en memoria).
 * Los datos del cliente y su contraseña ({@link DatosCliente#CONTRASENA_VALIDA}) quedan en la memoria del
 * cliente.
 */
public class PrepararClienteActivo implements Task {

    private final Actor administrador;
    private final DatosCliente datos;

    public PrepararClienteActivo(Actor administrador, DatosCliente datos) {
        this.administrador = administrador;
        this.datos = datos;
    }

    /** Con un cliente de prueba nuevo (documento y correo únicos). */
    public static PrepararClienteActivo aprobadoPor(Actor administrador) {
        return aprobadoPor(administrador, DatosCliente.valido());
    }

    /** Con los datos indicados. */
    public static PrepararClienteActivo aprobadoPor(Actor administrador, DatosCliente datos) {
        return Tasks.instrumented(PrepararClienteActivo.class, administrador, datos);
    }

    @Override
    @Step("{0} queda registrado, con correo confirmado y aprobado por el administrador")
    public <T extends Actor> void performAs(T cliente) {
        cliente.attemptsTo(
                RegistrarCliente.con(datos),
                ConfirmarCorreo.definiendoLaContrasena(DatosCliente.CONTRASENA_VALIDA));
        administrador.attemptsTo(AprobarCliente.a(datos));
    }
}