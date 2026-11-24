package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import co.edu.udea.certificacion.digitalbank.questions.ResultadoDelLogin;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.tasks.AutenticarseConDobleFactor;
import co.edu.udea.certificacion.digitalbank.tasks.CambiarEstadoDelCliente;
import co.edu.udea.certificacion.digitalbank.tasks.IniciarSesion;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Entonces;
import net.serenitybdd.screenplay.Actor;

/** HU08: cambio de estado de un cliente. */
public class CambioEstadoClienteStepDefinitions {

    @Cuando("el administrador cambia el estado del cliente a {string}")
    public void administradorCambiaElEstado(String estado) {
        DatosCliente datos = Actores.cliente().recall("cliente");
        Actores.administrador().attemptsTo(CambiarEstadoDelCliente.a(estado, datos));
    }

    @Cuando("el administrador cambia el estado de un cliente que no existe a {string}")
    public void administradorCambiaElEstadoDeUnClienteInexistente(String estado) {
        Actores.administrador().attemptsTo(CambiarEstadoDelCliente.a(estado, DatosCliente.valido()));
    }

    @Cuando("un usuario sin rol de administrador intenta cambiar el estado del cliente a {string}")
    public void usuarioSinRolIntentaCambiarElEstado(String estado) {
        Actor cliente = Actores.cliente();
        DatosCliente datos = cliente.recall("cliente");
        cliente.attemptsTo(
                AutenticarseConDobleFactor.con(datos.correo(), DatosCliente.CONTRASENA_VALIDA),
                CambiarEstadoDelCliente.a(estado, datos));
    }

    @Entonces("el cambio de estado es aceptado")
    public void cambioAceptado() {
        Actores.administrador().should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(204)));
    }

    @Entonces("el cliente puede iniciar sesión")
    public void clientePuedeIniciarSesion() {
        Actor cliente = Actores.cliente();
        DatosCliente datos = cliente.recall("cliente");
        cliente.attemptsTo(IniciarSesion.con(datos.correo(), DatosCliente.CONTRASENA_VALIDA));
        cliente.should(
        seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)),
        seeThat(ResultadoDelLogin.solicitaSegundoFactor(), is(true)));
    }
}
