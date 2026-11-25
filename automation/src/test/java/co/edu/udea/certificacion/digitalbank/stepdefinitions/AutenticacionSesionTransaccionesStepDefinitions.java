package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import co.edu.udea.certificacion.digitalbank.integrations.CodigosVerificacion.Proposito;
import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeVerificacion;
import co.edu.udea.certificacion.digitalbank.questions.EstadoDeLaTransaccion;
import co.edu.udea.certificacion.digitalbank.tasks.AutenticarseConDobleFactor;
import co.edu.udea.certificacion.digitalbank.tasks.ConfirmarSegundoFactor;
import co.edu.udea.certificacion.digitalbank.tasks.ConfirmarTransaccionCon2FA;
import co.edu.udea.certificacion.digitalbank.tasks.ConsultarMiPerfil;
import co.edu.udea.certificacion.digitalbank.tasks.ConsultarTransaccion;
import co.edu.udea.certificacion.digitalbank.tasks.IniciarRetiro;
import co.edu.udea.certificacion.digitalbank.tasks.IniciarSesion;
import co.edu.udea.certificacion.digitalbank.tasks.IniciarTransferencia;
import co.edu.udea.certificacion.digitalbank.utils.EntornoQa;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import java.time.Duration;
import java.util.Locale;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;

public class AutenticacionSesionTransaccionesStepDefinitions {

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
            assertEquals(400, SerenityRest.lastResponse().statusCode());
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
        assertNotNull(cliente.recall("token"));
        cliente.attemptsTo(ConsultarMiPerfil.conToken(cliente.recall("token")));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)));
    }

    @Entonces("el código de acceso queda invalidado y no se crea una sesión")
    public void codigoLoginInvalidado() {
        Actor cliente = Actores.cliente();
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(400)));
        assertNull(cliente.recall("token"));
    }

    @Entonces("el segundo uso del código es rechazado sin invalidar la sesión ya creada")
    public void codigoReutilizadoRechazado() {
        Actor cliente = Actores.cliente();
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(400)));
        cliente.attemptsTo(ConsultarMiPerfil.conToken(cliente.recall("token")));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)));
    }

    @Dado("que el cliente activo completó la autenticación de doble factor")
    public void clienteActivoAutenticado() {
        Actor cliente = Actores.cliente();
        DatosCliente datos = cliente.recall("cliente");
        cliente.attemptsTo(AutenticarseConDobleFactor.con(datos.correo(), DatosCliente.CONTRASENA_VALIDA));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)));
        String token = cliente.recall("token");
        cliente.remember("tokenAnterior", token);
    }

    @Cuando("permanece inactivo hasta superar el tiempo configurado de sesión")
    public void esperarInactividad() {
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

    @Dado("que el cliente con cuentas de prueba inició sesión")
    public void clienteCuentasPruebaAutenticado() {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(AutenticarseConDobleFactor.con(
                variableRequerida("ACCEPTANCE_CLIENT_EMAIL"),
                variableRequerida("ACCEPTANCE_CLIENT_PASSWORD")));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)));
    }

    @Dado("que el cliente tiene una transferencia pendiente de autorización")
    public void transferenciaPendiente() {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(IniciarTransferencia.entre(
                variableRequerida("ACCEPTANCE_ORIGIN_ACCOUNT"),
                variableRequerida("ACCEPTANCE_DESTINATION_ACCOUNT"),
                valorConDefecto("ACCEPTANCE_TRANSFER_AMOUNT", "1.00")));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(201)));
    }

    @Dado("que el cliente tiene un retiro pendiente de autorización")
    public void retiroPendiente() {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(IniciarRetiro.deCuenta(
                variableRequerida("ACCEPTANCE_ORIGIN_ACCOUNT"),
                valorConDefecto("ACCEPTANCE_WITHDRAWAL_AMOUNT", "1.00")));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(201)));
    }

    @Cuando("confirma la transacción con el código correcto")
    public void confirmarTransaccionCorrecta() {
        Actores.cliente().attemptsTo(ConfirmarTransaccionCon2FA.conCodigoDelBuzon());
    }

    @Cuando("confirma la transacción con un código incorrecto")
    public void confirmarTransaccionIncorrecta() {
        Actor cliente = Actores.cliente();
        String codigoValido = cliente.asksFor(CodigoDeVerificacion.enviadoA(
                cliente.recall("correoSesion"), Proposito.TRANSACTION));
        cliente.remember("codigoTransaccion", codigoValido);
        cliente.attemptsTo(ConfirmarTransaccionCon2FA.conCodigo(codigoIncorrecto(0, codigoValido)));
    }

    @Cuando("envía tres códigos incorrectos para la transacción")
    public void agotarIntentosTransaccion() {
        Actor cliente = Actores.cliente();
        String codigoValido = cliente.asksFor(CodigoDeVerificacion.enviadoA(
                cliente.recall("correoSesion"), Proposito.TRANSACTION));
        for (int intento = 0; intento < 3; intento++) {
            cliente.attemptsTo(ConfirmarTransaccionCon2FA.conCodigo(codigoIncorrecto(intento, codigoValido)));
            assertEquals(intento < 2 ? 400 : 429, SerenityRest.lastResponse().statusCode());
        }
    }

    @Entonces("la transacción queda completada")
    public void transaccionCompletada() {
        Actor cliente = Actores.cliente();
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)));
        cliente.attemptsTo(ConsultarTransaccion.actual());
        cliente.should(seeThat(EstadoDeLaTransaccion.enLaRespuesta(), equalTo("COMPLETED")));
    }

    @Entonces("la transacción permanece pendiente")
    public void transaccionPendiente() {
        Actor cliente = Actores.cliente();
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(400)));
        cliente.attemptsTo(ConsultarTransaccion.actual());
        cliente.should(seeThat(EstadoDeLaTransaccion.enLaRespuesta(), equalTo("PENDING")));
    }

    @Entonces("la transacción queda cancelada")
    public void transaccionCancelada() {
        Actor cliente = Actores.cliente();
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(429)));
        cliente.attemptsTo(ConsultarTransaccion.actual());
        cliente.should(seeThat(EstadoDeLaTransaccion.enLaRespuesta(), equalTo("REJECTED")));
    }

    private static String codigoIncorrecto(int semilla, String codigoValido) {
        int candidato = semilla;
        String codigo = String.format(Locale.ROOT, "%06d", candidato);
        while (codigo.equals(codigoValido)) {
            codigo = String.format(Locale.ROOT, "%06d", ++candidato);
        }
        return codigo;
    }

    private static String variableRequerida(String nombre) {
        String valor = EntornoQa.valor(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Define la variable de ambiente " + nombre);
        }
        return valor;
    }

    private static String valorConDefecto(String nombre, String defecto) {
        String valor = EntornoQa.valor(nombre);
        return valor == null || valor.isBlank() ? defecto : valor;
    }
}
