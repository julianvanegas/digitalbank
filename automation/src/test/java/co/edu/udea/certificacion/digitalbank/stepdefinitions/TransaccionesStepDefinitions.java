package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyOrNullString;

import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.ComprobanteDeLaTransaccion;
import co.edu.udea.certificacion.digitalbank.questions.EstadoDeLaTransaccion;
import co.edu.udea.certificacion.digitalbank.questions.provisional.SaldoDeLaCuenta;
import co.edu.udea.certificacion.digitalbank.tasks.AutenticarseConDobleFactor;
import co.edu.udea.certificacion.digitalbank.tasks.BloquearCuenta;
import co.edu.udea.certificacion.digitalbank.tasks.PrepararClienteActivo;
import co.edu.udea.certificacion.digitalbank.tasks.provisional.AbrirCuenta;
import co.edu.udea.certificacion.digitalbank.tasks.provisional.RealizarConsignacion;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import java.math.BigDecimal;
import net.serenitybdd.screenplay.Actor;

/**
 * Pasos compartidos por HU19 (retiros), HU20 (transferencias) y el E2E: preparar cuentas con saldo y verificar
 * saldos y comprobantes. Las cuentas del cliente quedan en su memoria como {@code cuentaOrigen} y
 * {@code cuentaDestino}; la del otro cliente, en la memoria de "Cliente existente" como {@code cuentaOrigen}.
 */
public class TransaccionesStepDefinitions {

    static final String CUENTA_ORIGEN = "cuentaOrigen";
    static final String CUENTA_DESTINO = "cuentaDestino";

    @Dado("el cliente inició sesión con doble factor")
    public void clienteInicioSesion() {
        iniciarSesion(Actores.cliente());
    }

    @Dado("tiene una cuenta con saldo de {long} pesos")
    public void tieneCuentaConSaldo(long saldo) {
        abrirCuentaConSaldo(Actores.cliente(), CUENTA_ORIGEN, saldo);
    }

    @Dado("tiene otra cuenta propia sin saldo")
    public void tieneOtraCuentaSinSaldo() {
        abrirCuentaConSaldo(Actores.cliente(), CUENTA_DESTINO, 0);
    }

    @Dado("que otro cliente tiene una cuenta con saldo de {long} pesos")
    public void otroClienteConCuenta(long saldo) {
        Actor otro = Actores.clienteExistente();
        otro.attemptsTo(PrepararClienteActivo.aprobadoPor(Actores.administrador()));
        iniciarSesion(otro);
        abrirCuentaConSaldo(otro, CUENTA_ORIGEN, saldo);
    }

    @Dado("el administrador bloquea la cuenta de origen")
    public void bloqueaCuentaOrigen() {
        bloquear(Actores.cliente().recall(CUENTA_ORIGEN));
    }

    @Dado("el administrador bloquea la cuenta de destino")
    public void bloqueaCuentaDestino() {
        bloquear(Actores.cliente().recall(CUENTA_DESTINO));
    }

    @Entonces("la transacción queda en estado {string}")
    public void transaccionEnEstado(String estado) {
        Actores.cliente().should(seeThat(EstadoDeLaTransaccion.enLaRespuesta(), equalTo(estado)));
    }

    @Entonces("recibe el comprobante de la operación")
    public void recibeComprobante() {
        Actores.cliente().should(
                seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)),
                seeThat(ComprobanteDeLaTransaccion.enLaRespuesta(), not(isEmptyOrNullString())));
    }

    @Entonces("el saldo de la cuenta de origen es {long} pesos")
    public void saldoOrigen(long saldo) {
        verificarSaldo(CUENTA_ORIGEN, saldo);
    }

    @Entonces("el saldo de la cuenta de destino es {long} pesos")
    public void saldoDestino(long saldo) {
        verificarSaldo(CUENTA_DESTINO, saldo);
    }

    static void iniciarSesion(Actor actor) {
        DatosCliente datos = actor.recall("cliente");
        actor.attemptsTo(AutenticarseConDobleFactor.con(datos.correo(), DatosCliente.CONTRASENA_VALIDA));
        actor.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)));
    }

    /** El actor abre una cuenta y, si el saldo es mayor que cero, el administrador la consigna. */
    static void abrirCuentaConSaldo(Actor actor, String nombre, long saldo) {
        actor.attemptsTo(AbrirCuenta.deAhorros());
        actor.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(201)));
        String numero = actor.recall("ultimaCuenta");
        actor.remember(nombre, numero);
        if (saldo > 0) {
            Actor administrador = Actores.administrador();
            administrador.attemptsTo(RealizarConsignacion.enLaCuenta(numero, saldo));
            administrador.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(201)));
        }
    }

    private static void bloquear(String cuenta) {
        Actor administrador = Actores.administrador();
        administrador.attemptsTo(BloquearCuenta.numero(cuenta));
        administrador.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)));
    }

    private static void verificarSaldo(String nombre, long saldo) {
        Actor cliente = Actores.cliente();
        String cuenta = cliente.recall(nombre);
        cliente.should(seeThat(SaldoDeLaCuenta.numero(cuenta), comparesEqualTo(BigDecimal.valueOf(saldo))));
    }
}
