package co.edu.udea.certificacion.digitalbank.tasks;

import net.serenitybdd.annotations.Step;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

/**
 * Tarea compuesta "cuenta abierta y con saldo": el cliente abre una cuenta (HU10) y, si el saldo pedido es mayor que
 * cero, el administrador la consigna (HU18). El número queda en la memoria del cliente como {@code ultimaCuenta}.
 *
 * <p>Solo encadena tareas: no verifica. Quien la usa confirma el resultado preguntando el saldo de la cuenta
 * ({@code SaldoDeLaCuenta}), que comprueba a la vez que la cuenta existe y que la consignación se aplicó.
 */
public class CuentaAbiertaConSaldo implements Task {

    private final Actor administrador;
    private final long saldo;

    public CuentaAbiertaConSaldo(Actor administrador, long saldo) {
        this.administrador = administrador;
        this.saldo = saldo;
    }

    public static CuentaAbiertaConSaldo de(long saldo, Actor administrador) {
        return Tasks.instrumented(CuentaAbiertaConSaldo.class, administrador, saldo);
    }

    @Override
    @Step("{0} abre una cuenta de ahorros con saldo de #saldo pesos")
    public <T extends Actor> void performAs(T cliente) {
        cliente.attemptsTo(AbrirCuenta.deAhorros());
        if (SerenityRest.lastResponse().statusCode() == 201 && saldo > 0) {
            administrador.attemptsTo(RealizarConsignacion.enLaCuenta(cliente.recall("ultimaCuenta"), saldo));
        }
    }
}
