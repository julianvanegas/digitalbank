package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.ResultadoDelLogin;
import co.edu.udea.certificacion.digitalbank.tasks.AutenticarseConDobleFactor;
import co.edu.udea.certificacion.digitalbank.tasks.ConsultarMiPerfil;
import co.edu.udea.certificacion.digitalbank.tasks.IniciarSesion;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import net.serenitybdd.screenplay.Actor;

/** HU02: inicio de sesión. */
public class InicioSesionStepDefinitions {

    private static final String CONTRASENA_INCORRECTA = "Incorrecta#1";

    @Cuando("inicia sesión con sus credenciales válidas")
    public void iniciaSesionConCredencialesValidas() {
        DatosCliente datos = Actores.cliente().recall("cliente");
        Actores.cliente().attemptsTo(IniciarSesion.con(datos.correo(), DatosCliente.CONTRASENA_VALIDA));
    }

    @Cuando("inicia sesión con una contraseña incorrecta")
    public void iniciaSesionConContrasenaIncorrecta() {
        DatosCliente datos = Actores.cliente().recall("cliente");
        Actores.cliente().attemptsTo(IniciarSesion.con(datos.correo(), CONTRASENA_INCORRECTA));
    }

    @Cuando("se equivoca de contraseña {int} veces seguidas")
    public void seEquivocaDeContrasena(int veces) {
        DatosCliente datos = Actores.cliente().recall("cliente");
        for (int intento = 0; intento < veces; intento++) {
            Actores.cliente().attemptsTo(IniciarSesion.con(datos.correo(), CONTRASENA_INCORRECTA));
        }
    }

    @Entonces("el sistema le solicita el segundo factor")
    public void elSistemaSolicitaElSegundoFactor() {
        Actores.cliente().should(
                seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)),
                seeThat(ResultadoDelLogin.solicitaSegundoFactor(), is(true)));
    }

    @Entonces("no puede iniciar sesión aunque use la contraseña correcta")
    public void noPuedeIniciarSesion() {
        DatosCliente datos = Actores.cliente().recall("cliente");
        Actores.cliente().attemptsTo(IniciarSesion.con(datos.correo(), DatosCliente.CONTRASENA_VALIDA));
        Actores.cliente().should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(403)));
    }

    @Dado("que el cliente tiene una sesión iniciada")
    public void clienteConSesionIniciada() {
        Actor cliente = Actores.cliente();
        DatosCliente datos = cliente.recall("cliente");
        cliente.attemptsTo(AutenticarseConDobleFactor.con(datos.correo(), DatosCliente.CONTRASENA_VALIDA));
        String token = cliente.recall("token");
        cliente.remember("tokenAnterior", token);
        cliente.attemptsTo(ConsultarMiPerfil.conToken(token));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)));
    }

    @Cuando("inicia sesión nuevamente desde otro dispositivo")
    public void iniciaSesionNuevamente() {
        DatosCliente datos = Actores.cliente().recall("cliente");
        Actores.cliente().attemptsTo(
                AutenticarseConDobleFactor.con(datos.correo(), DatosCliente.CONTRASENA_VALIDA));
    }

    @Entonces("la sesión anterior es rechazada")
    public void laSesionAnteriorEsRechazada() {
        Actor cliente = Actores.cliente();
        String tokenAnterior = cliente.recall("tokenAnterior");
        cliente.attemptsTo(ConsultarMiPerfil.conToken(tokenAnterior));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(401)));
    }

    @Entonces("la nueva sesión sigue vigente")
    public void laNuevaSesionSigueVigente() {
        Actor cliente = Actores.cliente();
        String token = cliente.recall("token");
        cliente.attemptsTo(ConsultarMiPerfil.conToken(token));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)));
    }
}