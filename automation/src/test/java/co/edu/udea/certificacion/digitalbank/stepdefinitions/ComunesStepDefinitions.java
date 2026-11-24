package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.EstadoDelCliente;
import co.edu.udea.certificacion.digitalbank.questions.MensajeDeError;
import co.edu.udea.certificacion.digitalbank.tasks.AutenticarseConDobleFactor;
import co.edu.udea.certificacion.digitalbank.tasks.CambiarEstadoDelCliente;
import co.edu.udea.certificacion.digitalbank.tasks.ConfirmarCorreo;
import co.edu.udea.certificacion.digitalbank.tasks.ConsultarCliente;
import co.edu.udea.certificacion.digitalbank.tasks.PrepararClienteActivo;
import co.edu.udea.certificacion.digitalbank.tasks.RegistrarCliente;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import net.serenitybdd.screenplay.Actor;

/** Pasos compartidos por las features de HU01, HU02 y HU08: precondiciones y verificaciones generales. */
public class ComunesStepDefinitions {

    @Dado("que el administrador inició sesión en el banco")
    public void administradorInicioSesion() {
        Actor administrador = Actores.administrador();
        administrador.attemptsTo(AutenticarseConDobleFactor.comoAdministrador());
        administrador.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)));
    }

    @Dado("que existe un cliente pendiente de revisión")
    public void clientePendienteDeRevision() {
        registrarYConfirmarCorreo();
    }

    @Dado("que existe un cliente activo")
    public void clienteActivo() {
        Actores.cliente().attemptsTo(PrepararClienteActivo.aprobadoPor(Actores.administrador()));
        Actores.administrador().should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(204)));
    }

    @Dado("que existe un cliente bloqueado")
    public void clienteBloqueado() {
        clienteActivo();
        DatosCliente datos = Actores.cliente().recall("cliente");
        Actores.administrador().attemptsTo(CambiarEstadoDelCliente.a("BLOCKED", datos));
        Actores.administrador().should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(204)));
    }

    @Entonces("recibe el rechazo con código {int}")
    public void recibeElRechazo(int codigo) {
        Actores.cliente().should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(codigo)));
    }

    @Entonces("ve un mensaje que contiene {string}")
    public void veUnMensaje(String texto) {
        Actores.cliente().should(seeThat(MensajeDeError.delServidor(), containsString(texto)));
    }

    @Entonces("el estado del cliente es {string}")
    public void elEstadoDelClienteEs(String estado) {
        DatosCliente datos = Actores.cliente().recall("cliente");
        Actores.administrador().attemptsTo(ConsultarCliente.conDocumento(datos));
        Actores.administrador().should(seeThat(EstadoDelCliente.enLaRespuesta(), equalTo(estado)));
    }

    /** Registra al cliente y confirma su correo, con las verificaciones de que cada paso funcionó. */
    private DatosCliente registrarYConfirmarCorreo() {
        Actor cliente = Actores.cliente();
        DatosCliente datos = DatosCliente.valido();
        cliente.attemptsTo(RegistrarCliente.con(datos));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(201)));
        cliente.attemptsTo(ConfirmarCorreo.definiendoLaContrasena(DatosCliente.CONTRASENA_VALIDA));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(204)));
        return datos;
    }
}