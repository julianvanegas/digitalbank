package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;

import co.edu.udea.certificacion.digitalbank.questions.CampoDeLaRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.ComprobanteDeLaTransaccion;
import co.edu.udea.certificacion.digitalbank.questions.EstadoDeLaTransaccion;
import co.edu.udea.certificacion.digitalbank.questions.MontoDeLaTransaccion;
import co.edu.udea.certificacion.digitalbank.tasks.RealizarConsignacion;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Entonces;
import java.math.BigDecimal;
import net.serenitybdd.screenplay.Actor;

/**
 * HU18: consignaciones del administrador. Autor original: integrante C. La cuenta de destino es la
 * {@code cuentaOrigen} del cliente, que preparan los pasos de {@link TransaccionesStepDefinitions}.
 */
public class ConsignacionStepDefinitions {

    @Cuando("el administrador consigna {long} pesos en la cuenta de origen")
    public void administradorConsigna(long monto) {
        Actores.administrador().attemptsTo(RealizarConsignacion.enLaCuenta(cuentaDelCliente(), monto));
    }

    @Cuando("el administrador consigna {long} pesos en una cuenta que no existe")
    public void administradorConsignaEnCuentaInexistente(long monto) {
        Actores.administrador().attemptsTo(RealizarConsignacion.enLaCuenta(Preparacion.CUENTA_INEXISTENTE, monto));
    }

    @Cuando("el cliente intenta consignar {long} pesos en su cuenta")
    public void clienteIntentaConsignar(long monto) {
        Actores.cliente().attemptsTo(RealizarConsignacion.enLaCuenta(cuentaDelCliente(), monto));
    }

    @Cuando("el administrador realiza dos consignaciones de {long} pesos")
    public void administradorRealizaDosConsignaciones(long monto) {
        Actor administrador = Actores.administrador();
        administrador.attemptsTo(RealizarConsignacion.enLaCuenta(cuentaDelCliente(), monto));
        administrador.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(201)));
        administrador.remember("primerComprobante", administrador.asksFor(ComprobanteDeLaTransaccion.enLaRespuesta()));
        administrador.attemptsTo(RealizarConsignacion.enLaCuenta(cuentaDelCliente(), monto));
        administrador.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(201)));
    }

    /** El comprobante es una consignación completada, por el monto pedido y en la cuenta del cliente. */
    @Entonces("recibe el comprobante de la consignación por {long} pesos")
    public void recibeComprobanteDeConsignacion(long monto) {
        Actores.administrador().should(
                seeThat(CodigoDeRespuesta.delServidor(), equalTo(201)),
                seeThat(ComprobanteDeLaTransaccion.enLaRespuesta(), not(isEmptyOrNullString())),
                seeThat(CampoDeLaRespuesta.llamado("type"), equalTo("DEPOSIT")),
                seeThat(CampoDeLaRespuesta.llamado("receiverAccountNumber"), equalTo(cuentaDelCliente())),
                seeThat(MontoDeLaTransaccion.enLaRespuesta(), comparesEqualTo(BigDecimal.valueOf(monto))),
                seeThat(EstadoDeLaTransaccion.enLaRespuesta(), equalTo("COMPLETED")));
    }

    @Entonces("los comprobantes de las consignaciones son diferentes")
    public void comprobantesSonDiferentes() {
        Actor administrador = Actores.administrador();
        String primero = administrador.recall("primerComprobante");
        administrador.should(
                seeThat(ComprobanteDeLaTransaccion.enLaRespuesta(), not(isEmptyOrNullString())),
                seeThat(ComprobanteDeLaTransaccion.enLaRespuesta(), not(equalTo(primero))));
    }

    private static String cuentaDelCliente() {
        return Actores.cliente().recall(TransaccionesStepDefinitions.CUENTA_ORIGEN);
    }
}
