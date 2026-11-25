package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import co.edu.udea.certificacion.digitalbank.tasks.TransferirConDobleFactor;
import co.edu.udea.certificacion.digitalbank.tasks.TransferirFondos;
import io.cucumber.java.es.Cuando;
import net.serenitybdd.screenplay.Actor;

/** HU20: transferencias. Las cuentas se preparan con los pasos de {@link TransaccionesStepDefinitions}. */
public class TransferenciaStepDefinitions {

    /** Doce dígitos que el generador del backend no asigna en la práctica: sirve como cuenta inexistente. */
    private static final String CUENTA_INEXISTENTE = "000000000001";

    @Cuando("transfiere {long} pesos a su otra cuenta y confirma con el código de verificación")
    public void transfiereYConfirma(long monto) {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(TransferirConDobleFactor.desde(origen(cliente), destino(cliente), monto));
    }

    @Cuando("solicita transferir {long} pesos a su otra cuenta")
    public void solicitaTransferir(long monto) {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(TransferirFondos.desde(origen(cliente), destino(cliente), monto));
    }

    @Cuando("solicita transferir {long} pesos a la misma cuenta")
    public void transfiereALaMismaCuenta(long monto) {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(TransferirFondos.desde(origen(cliente), origen(cliente), monto));
    }

    @Cuando("solicita transferir {long} pesos a una cuenta que no existe")
    public void transfiereACuentaInexistente(long monto) {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(TransferirFondos.desde(origen(cliente), CUENTA_INEXISTENTE, monto));
    }

    private static String origen(Actor cliente) {
        return cliente.recall(TransaccionesStepDefinitions.CUENTA_ORIGEN);
    }

    private static String destino(Actor cliente) {
        return cliente.recall(TransaccionesStepDefinitions.CUENTA_DESTINO);
    }
}
