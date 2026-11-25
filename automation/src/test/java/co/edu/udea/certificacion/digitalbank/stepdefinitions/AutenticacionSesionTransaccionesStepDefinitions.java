package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import co.edu.udea.certificacion.digitalbank.integrations.CodigosVerificacion.Proposito;
import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeVerificacion;
import co.edu.udea.certificacion.digitalbank.questions.EstadoDeLaTransaccion;
import co.edu.udea.certificacion.digitalbank.tasks.CerrarSesion;
import co.edu.udea.certificacion.digitalbank.tasks.ConfirmarSegundoFactor;
import co.edu.udea.certificacion.digitalbank.tasks.ConfirmarTransaccion;
import co.edu.udea.certificacion.digitalbank.tasks.ConsultarMiPerfil;
import co.edu.udea.certificacion.digitalbank.tasks.ConsultarTransaccion;
import co.edu.udea.certificacion.digitalbank.tasks.IniciarSesion;
import co.edu.udea.certificacion.digitalbank.tasks.RetirarDinero;
import co.edu.udea.certificacion.digitalbank.tasks.TransferirFondos;
import co.edu.udea.certificacion.digitalbank.utils.EntornoQa;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import java.time.Duration;
import java.util.Locale;
import net.serenitybdd.screenplay.Actor;

/**
 * HU03 (segundo factor del login), HU04 (sesión) y HU21 (segundo factor de retiros y transferencias). Las cuentas
 * de HU21 se preparan con los pasos de {@link TransaccionesStepDefinitions}, así que cada escenario crea sus datos.
 */
public class AutenticacionSesionTransaccionesStepDefinitions {

    /** Monto de las transacciones pendientes de HU21: basta con que sea menor que el saldo preparado. */
    private static final long MONTO_TRANSACCION = 10000;

    // ---------- HU03: segundo factor del login ----------

    @Dado("que el cliente activo inició sesión y recibió el código 2FA")
    public void clienteActivoConCodigoLogin() {
        Actor cliente = Actores.cliente();
        DatosCliente datos = cliente.recall("cliente");
        cliente.attemptsTo(IniciarSesion.con(datos.correo(), DatosCliente.CONTRASENA_VALIDA));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)));
        cliente.remember("codigoLogin",
                cliente.asksFor(CodigoDeVerificacion.enviadoA(datos.correo(), Proposito.LOGIN)));
    }

    @Cuando("verifica el código correcto de acceso")
    public void verificarCodigoCorrecto() {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(ConfirmarSegundoFactor.conCodigo(cliente.recall("codigoLogin")));
    }

    @Cuando("ingresa tres códigos incorrectos y luego intenta verificar de nuevo")
    public void agotarIntentosLogin() {
        Actor cliente = Actores.cliente();
        String codigoValido = cliente.recall("codigoLogin");
        for (int intento = 0; intento < 4; intento++) {
            cliente.attemptsTo(ConfirmarSegundoFactor.conCodigo(codigoIncorrecto(intento, codigoValido)));
            cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(400)));
        }
    }

    @Cuando("intenta utilizar por segunda vez el mismo código de acceso")
    public void reutilizarCodigoLogin() {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(ConfirmarSegundoFactor.conCodigo(cliente.recall("codigoLogin")));
    }

    @Entonces("el cliente puede acceder a sus cuentas")
    public void clientePuedeAcceder() {
        Actor cliente = Actores.cliente();
        assertNotNull("El login no dejó un token de sesión", cliente.recall("token"));
        cliente.attemptsTo(ConsultarMiPerfil.conToken(cliente.recall("token")));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)));
    }

    @Entonces("el código de acceso queda invalidado y no se crea una sesión")
    public void codigoLoginInvalidado() {
        Actor cliente = Actores.cliente();
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(400)));
        assertNull("No debió crearse una sesión", cliente.recall("token"));
    }

    @Entonces("el segundo uso del código es rechazado sin invalidar la sesión ya creada")
    public void codigoReutilizadoRechazado() {
        Actor cliente = Actores.cliente();
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(400)));
        cliente.attemptsTo(ConsultarMiPerfil.conToken(cliente.recall("token")));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)));
    }

    // ---------- HU04: sesión ----------

    @Cuando("cierra la sesión")
    public void cierraLaSesion() {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(CerrarSesion.ahora());
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(204)));
    }

    @Cuando("permanece inactivo hasta superar el tiempo configurado de sesión")
    public void esperarInactividad() {
        Actor cliente = Actores.cliente();
        String token = cliente.recall("token");
        cliente.remember("tokenAnterior", token);
        long esperaMs = Long.parseLong(valorConDefecto(
                "ACCEPTANCE_INACTIVITY_WAIT_MS", String.valueOf(Duration.ofMinutes(5).plusSeconds(1).toMillis())));
        try {
            Thread.sleep(esperaMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La espera de inactividad fue interrumpida", e);
        }
    }

    @Entonces("el token anterior es rechazado")
    public void tokenAnteriorRechazado() {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(ConsultarMiPerfil.conToken(cliente.recall("tokenAnterior")));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(401)));
    }

    // ---------- HU21: segundo factor de retiros y transferencias ----------

    @Dado("que el cliente tiene una transferencia pendiente de autorización")
    public void transferenciaPendiente() {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(TransferirFondos.desde(
                cliente.recall(TransaccionesStepDefinitions.CUENTA_ORIGEN),
                cliente.recall(TransaccionesStepDefinitions.CUENTA_DESTINO),
                MONTO_TRANSACCION));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(201)));
    }

    @Dado("que el cliente tiene un retiro pendiente de autorización")
    public void retiroPendiente() {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(RetirarDinero.deLaCuenta(
                cliente.recall(TransaccionesStepDefinitions.CUENTA_ORIGEN), MONTO_TRANSACCION));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(201)));
    }

    @Cuando("confirma la transacción con el código correcto")
    public void confirmarTransaccionCorrecta() {
        Actores.cliente().attemptsTo(ConfirmarTransaccion.conElCodigoRecibido());
    }

    @Cuando("confirma la transacción con un código incorrecto")
    public void confirmarTransaccionIncorrecta() {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(ConfirmarTransaccion.conElCodigo(codigoIncorrecto(0, codigoDeTransaccion(cliente))));
    }

    @Cuando("envía tres códigos incorrectos para la transacción")
    public void agotarIntentosTransaccion() {
        Actor cliente = Actores.cliente();
        String codigoValido = codigoDeTransaccion(cliente);
        for (int intento = 0; intento < 3; intento++) {
            cliente.attemptsTo(ConfirmarTransaccion.conElCodigo(codigoIncorrecto(intento, codigoValido)));
            cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(intento < 2 ? 400 : 429)));
        }
    }

    @Entonces("la transacción queda completada")
    public void transaccionCompletada() {
        verificarTransaccion(200, "COMPLETED");
    }

    @Entonces("la transacción permanece pendiente")
    public void transaccionPendiente() {
        verificarTransaccion(400, "PENDING");
    }

    @Entonces("la transacción queda cancelada")
    public void transaccionCancelada() {
        verificarTransaccion(429, "REJECTED");
    }

    /** Verifica el código de la confirmación y después consulta el estado con que quedó la transacción. */
    private static void verificarTransaccion(int codigoHttp, String estado) {
        Actor cliente = Actores.cliente();
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(codigoHttp)));
        cliente.attemptsTo(ConsultarTransaccion.actual());
        cliente.should(seeThat(EstadoDeLaTransaccion.enLaRespuesta(), equalTo(estado)));
    }

    private static String codigoDeTransaccion(Actor cliente) {
        return cliente.asksFor(CodigoDeVerificacion.enviadoA(cliente.recall("correoSesion"), Proposito.TRANSACTION));
    }

    /** Un código de 6 dígitos distinto del válido, para simular un error del cliente. */
    private static String codigoIncorrecto(int semilla, String codigoValido) {
        int candidato = semilla;
        String codigo = String.format(Locale.ROOT, "%06d", candidato);
        while (codigo.equals(codigoValido)) {
            codigo = String.format(Locale.ROOT, "%06d", ++candidato);
        }
        return codigo;
    }

    private static String valorConDefecto(String nombre, String defecto) {
        String valor = EntornoQa.valor(nombre);
        return valor == null || valor.isBlank() ? defecto : valor;
    }
}
