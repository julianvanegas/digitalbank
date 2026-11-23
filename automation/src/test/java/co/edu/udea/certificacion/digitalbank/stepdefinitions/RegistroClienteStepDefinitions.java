package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.equalTo;

import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.EstadoDelCliente;
import co.edu.udea.certificacion.digitalbank.tasks.ConfirmarCorreo;
import co.edu.udea.certificacion.digitalbank.tasks.ConsultarCliente;
import co.edu.udea.certificacion.digitalbank.tasks.RegistrarCliente;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import java.time.LocalDate;
import net.serenitybdd.screenplay.Actor;

/** HU01: registro de clientes. */
public class RegistroClienteStepDefinitions {

    private DatosCliente datos;
    private DatosCliente existente;

    @Dado("que un usuario mayor de edad con documento y correo no registrados")
    public void usuarioMayorDeEdad() {
        datos = DatosCliente.valido();
    }

    @Dado("un usuario menor de 18 años")
    public void usuarioMenorDeEdad() {
        datos = DatosCliente.valido().conFechaNacimiento(LocalDate.now().minusYears(17));
    }

    @Dado("que ya existe un cliente registrado")
    public void yaExisteUnCliente() {
        existente = DatosCliente.valido();
        Actor clienteExistente = Actores.clienteExistente();
        clienteExistente.attemptsTo(RegistrarCliente.con(existente));
        clienteExistente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(201)));
    }

    @Dado("un usuario nuevo intenta registrarse con el mismo número de documento")
    public void usuarioConElMismoDocumento() {
        datos = DatosCliente.valido().conNumeroDocumento(existente.numeroDocumento());
    }

    @Dado("un usuario nuevo intenta registrarse con el mismo correo")
    public void usuarioConElMismoCorreo() {
        datos = DatosCliente.valido().conCorreo(existente.correo());
    }

    @Dado("que un cliente se registró y tiene su correo pendiente de confirmar")
    public void clienteConCorreoPendiente() {
        datos = DatosCliente.valido();
        Actores.cliente().attemptsTo(RegistrarCliente.con(datos));
        Actores.cliente().should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(201)));
    }

    @Cuando("solicita registrarse")
    public void solicitaRegistrarse() {
        Actores.cliente().attemptsTo(RegistrarCliente.con(datos));
    }

    @Cuando("confirma su correo con la contraseña {string}")
    public void confirmaSuCorreo(String contrasena) {
        Actores.cliente().attemptsTo(ConfirmarCorreo.definiendoLaContrasena(contrasena));
    }

    @Entonces("recibe el registro aceptado con código {int}")
    public void registroAceptado(int codigo) {
        Actores.cliente().should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(codigo)));
    }

    @Entonces("el cliente queda en estado {string}")
    public void clienteQuedaEnEstado(String estado) {
        Actores.cliente().should(seeThat(EstadoDelCliente.enLaRespuesta(), equalTo(estado)));
    }

    @Entonces("el administrador no encuentra al cliente registrado")
    public void clienteNoRegistrado() {
        Actores.administrador().attemptsTo(ConsultarCliente.conDocumento(datos));
        Actores.administrador().should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(404)));
    }
}
