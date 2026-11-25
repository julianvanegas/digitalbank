package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;

import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import co.edu.udea.certificacion.digitalbank.questions.CampoDeLaRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.SaldoDeLaCuenta;
import co.edu.udea.certificacion.digitalbank.tasks.AutenticarseConDobleFactor;
import co.edu.udea.certificacion.digitalbank.tasks.ConsultarMiPerfil;
import co.edu.udea.certificacion.digitalbank.tasks.CuentaAbiertaConSaldo;
import co.edu.udea.certificacion.digitalbank.tasks.PrepararClienteActivo;
import java.math.BigDecimal;
import net.serenitybdd.screenplay.Actor;

/**
 * Preparaciones que comparten varias clases de pasos, para no repetirlas en cada una. No define pasos de Gherkin:
 * las clases de pasos la llaman. Cada preparación verifica su resultado, para que un escenario falle en el paso que
 * causó el problema y no más adelante.
 */
final class Preparacion {

    /** Doce dígitos que el generador del backend no asigna en la práctica: sirve como cuenta inexistente. */
    static final String CUENTA_INEXISTENTE = "000000000001";

    private Preparacion() {
    }

    /** El actor se registra, confirma su correo y el administrador lo aprueba (el administrador ya inició sesión). */
    static void clienteActivo(Actor actor) {
        actor.attemptsTo(PrepararClienteActivo.aprobadoPor(Actores.administrador()));
        Actores.administrador().should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(204)));
    }

    /** El actor inicia sesión con su correo y la contraseña de prueba, y completa el segundo factor. */
    static void iniciarSesion(Actor actor) {
        DatosCliente datos = actor.recall("cliente");
        actor.attemptsTo(AutenticarseConDobleFactor.con(datos.correo(), DatosCliente.CONTRASENA_VALIDA));
        actor.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)));
    }

    /**
     * El actor abre una cuenta con el saldo indicado y la guarda en su memoria con el nombre dado. Verifica con el
     * saldo que la cuenta existe y que la consignación se aplicó.
     */
    static String cuentaConSaldo(Actor actor, String nombre, long saldo) {
        actor.attemptsTo(CuentaAbiertaConSaldo.de(saldo, Actores.administrador()));
        String numero = actor.recall("ultimaCuenta");
        actor.remember(nombre, numero);
        actor.should(seeThat(SaldoDeLaCuenta.numero(numero), comparesEqualTo(BigDecimal.valueOf(saldo))));
        return numero;
    }

    /** Con el token actual del actor, su perfil responde 200 y es el suyo (mismo correo). */
    static void verificarQueSuSesionEsValida(Actor actor) {
        DatosCliente datos = actor.recall("cliente");
        actor.attemptsTo(ConsultarMiPerfil.conToken(actor.recall("token")));
        actor.should(
                seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)),
                seeThat(CampoDeLaRespuesta.llamado("email"), equalTo(datos.correo())));
    }
}
