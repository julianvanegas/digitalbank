package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.equalTo;

import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.tasks.RetirarConDobleFactor;
import co.edu.udea.certificacion.digitalbank.tasks.RetirarDinero;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import net.serenitybdd.screenplay.Actor;

/** HU19: retiros. Las cuentas se preparan con los pasos de {@link TransaccionesStepDefinitions}. */
public class RetiroStepDefinitions {

    @Dado("ya realizó {int} retiros de {long} pesos en el día")
    public void yaRealizoRetiros(int cantidad, long monto) {
        Actor cliente = Actores.cliente();
        String cuenta = cliente.recall(TransaccionesStepDefinitions.CUENTA_ORIGEN);
        for (int i = 0; i < cantidad; i++) {
            cliente.attemptsTo(RetirarConDobleFactor.deLaCuenta(cuenta, monto));
            cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)));
        }
    }

    @Cuando("retira {long} pesos y confirma con el código de verificación")
    public void retiraYConfirma(long monto) {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(RetirarConDobleFactor.deLaCuenta(cliente.recall(TransaccionesStepDefinitions.CUENTA_ORIGEN), monto));
    }

    @Cuando("solicita retirar {long} pesos")
    public void solicitaRetirar(long monto) {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(RetirarDinero.deLaCuenta(cliente.recall(TransaccionesStepDefinitions.CUENTA_ORIGEN), monto));
    }

    @Cuando("intenta retirar {long} pesos de la cuenta del otro cliente")
    public void retiraDeCuentaAjena(long monto) {
        String cuentaAjena = Actores.clienteExistente().recall(TransaccionesStepDefinitions.CUENTA_ORIGEN);
        Actores.cliente().attemptsTo(RetirarDinero.deLaCuenta(cuentaAjena, monto));
    }
}
