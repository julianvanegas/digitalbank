package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import co.edu.udea.certificacion.digitalbank.questions.CampoDeLaRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.SaldoDeLaCuenta;
import co.edu.udea.certificacion.digitalbank.tasks.ConsultarSaldo;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import java.math.BigDecimal;
import net.serenitybdd.screenplay.Actor;

/**
 * HU12: consulta de saldo. Autor original: integrante C. La cuenta propia es la {@code cuentaOrigen} que preparan
 * los pasos de {@link TransaccionesStepDefinitions}; la ajena, la del "Cliente existente".
 */
public class ConsultaSaldoStepDefinitions {

    @Dado("guarda el saldo actual de su cuenta")
    public void guardaSaldoActual() {
        Actor cliente = Actores.cliente();
        cliente.remember("saldoAntesDeConsultar",
                cliente.asksFor(SaldoDeLaCuenta.numero(cliente.recall(TransaccionesStepDefinitions.CUENTA_ORIGEN))));
    }

    @Cuando("el cliente consulta el saldo de su cuenta")
    public void clienteConsultaSaldo() {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(ConsultarSaldo.deLaCuenta(cliente.recall(TransaccionesStepDefinitions.CUENTA_ORIGEN)));
    }

    @Cuando("el cliente intenta consultar el saldo de la cuenta del otro cliente")
    public void clienteConsultaSaldoAjeno() {
        String cuentaAjena = Actores.clienteExistente().recall(TransaccionesStepDefinitions.CUENTA_ORIGEN);
        Actores.cliente().attemptsTo(ConsultarSaldo.deLaCuenta(cuentaAjena));
    }

    @Entonces("ve un saldo de {long} pesos en su cuenta")
    public void veSaldo(long saldo) {
        Actor cliente = Actores.cliente();
        cliente.should(
                seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)),
                seeThat(CampoDeLaRespuesta.llamado("accountNumber"),
                        equalTo(cliente.recall(TransaccionesStepDefinitions.CUENTA_ORIGEN))),
                seeThat(SaldoDeLaCuenta.enLaRespuesta(), comparesEqualTo(BigDecimal.valueOf(saldo))));
    }

    @Entonces("no ve información financiera de la cuenta")
    public void noVeInformacionFinanciera() {
        Actores.cliente().should(
                seeThat(CampoDeLaRespuesta.llamado("balance"), nullValue()),
                seeThat(CampoDeLaRespuesta.llamado("accountNumber"), nullValue()));
    }

    @Entonces("el saldo de la cuenta permanece sin modificaciones")
    public void saldoPermaneceSinCambios() {
        Actor cliente = Actores.cliente();
        BigDecimal saldoAntes = cliente.recall("saldoAntesDeConsultar");
        cliente.should(seeThat(
                SaldoDeLaCuenta.numero(cliente.recall(TransaccionesStepDefinitions.CUENTA_ORIGEN)),
                comparesEqualTo(saldoAntes)));
    }
}
