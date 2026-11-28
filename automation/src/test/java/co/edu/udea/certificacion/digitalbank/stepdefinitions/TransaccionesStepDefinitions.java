package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import co.edu.udea.certificacion.digitalbank.questions.CampoDeLaRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.ComprobanteDeLaTransaccion;
import co.edu.udea.certificacion.digitalbank.questions.EstadoDeLaTransaccion;
import co.edu.udea.certificacion.digitalbank.questions.MontoDeLaTransaccion;
import co.edu.udea.certificacion.digitalbank.questions.SaldoDeLaCuenta;
import co.edu.udea.certificacion.digitalbank.tasks.BloquearCuenta;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import java.math.BigDecimal;
import net.serenitybdd.screenplay.Actor;

/**
 * Pasos compartidos por HU19 (retiros), HU20 (transferencias), HU21 y el E2E: preparar cuentas con saldo y verificar
 * comprobantes y saldos. Las cuentas del cliente quedan en su memoria como {@code cuentaOrigen} y
 * {@code cuentaDestino}; la del otro cliente, en la memoria de "Cliente existente" como {@code cuentaOrigen}.
 */
public class TransaccionesStepDefinitions {

    static final String CUENTA_ORIGEN = "cuentaOrigen";
    static final String CUENTA_DESTINO = "cuentaDestino";

    @Dado("el cliente inició sesión con doble factor")
    public void clienteInicioSesion() {
        Preparacion.iniciarSesion(Actores.cliente());
    }

    @Dado("tiene una cuenta con saldo de {long} pesos")
    public void tieneCuentaConSaldo(long saldo) {
        Preparacion.cuentaConSaldo(Actores.cliente(), CUENTA_ORIGEN, saldo);
    }

    @Dado("tiene otra cuenta propia sin saldo")
    public void tieneOtraCuentaSinSaldo() {
        Preparacion.cuentaConSaldo(Actores.cliente(), CUENTA_DESTINO, 0);
    }

    @Dado("que otro cliente tiene una cuenta con saldo de {long} pesos")
    public void otroClienteConCuenta(long saldo) {
        Actor otro = Actores.clienteExistente();
        Preparacion.clienteActivo(otro);
        Preparacion.iniciarSesion(otro);
        Preparacion.cuentaConSaldo(otro, CUENTA_ORIGEN, saldo);
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

    @Entonces("recibe el comprobante del retiro por {long} pesos")
    public void recibeComprobanteDeRetiro(long monto) {
        Actor cliente = Actores.cliente();
        verificarComprobante(cliente, "WITHDRAWAL", monto);
        cliente.should(seeThat(CampoDeLaRespuesta.llamado("receiverAccountNumber"), nullValue()));
    }

    @Entonces("recibe el comprobante de la transferencia por {long} pesos")
    public void recibeComprobanteDeTransferencia(long monto) {
        Actor cliente = Actores.cliente();
        verificarComprobante(cliente, "TRANSFER", monto);
        cliente.should(seeThat(CampoDeLaRespuesta.llamado("receiverAccountNumber"),
                equalTo(cliente.recall(CUENTA_DESTINO))));
    }

    @Entonces("el saldo de la cuenta de origen es {long} pesos")
    public void saldoOrigen(long saldo) {
        verificarSaldo(CUENTA_ORIGEN, saldo);
    }

    @Entonces("el saldo de la cuenta de destino es {long} pesos")
    public void saldoDestino(long saldo) {
        verificarSaldo(CUENTA_DESTINO, saldo);
    }

    /**
     * El comprobante de la confirmación es el de la operación que inició el cliente (misma referencia), con su tipo,
     * su monto, la cuenta de origen y el estado COMPLETED.
     */
    private static void verificarComprobante(Actor cliente, String tipo, long monto) {
        cliente.should(
                seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)),
                seeThat(ComprobanteDeLaTransaccion.enLaRespuesta(), equalTo(cliente.recall("referenciaTransaccion"))),
                seeThat(CampoDeLaRespuesta.llamado("type"), equalTo(tipo)),
                seeThat(MontoDeLaTransaccion.enLaRespuesta(), comparesEqualTo(BigDecimal.valueOf(monto))),
                seeThat(CampoDeLaRespuesta.llamado("senderAccountNumber"), equalTo(cliente.recall(CUENTA_ORIGEN))),
                seeThat(EstadoDeLaTransaccion.enLaRespuesta(), equalTo("COMPLETED")));
    }

    private static void bloquear(String cuenta) {
        Actor administrador = Actores.administrador();
        administrador.attemptsTo(BloquearCuenta.numero(cuenta));
        administrador.should(
                seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)),
                seeThat(CampoDeLaRespuesta.llamado("status"), equalTo("BLOCKED")));
    }

    private static void verificarSaldo(String nombre, long saldo) {
        Actor cliente = Actores.cliente();
        String cuenta = cliente.recall(nombre);
        cliente.should(seeThat(SaldoDeLaCuenta.numero(cuenta), comparesEqualTo(BigDecimal.valueOf(saldo))));
    }
}
