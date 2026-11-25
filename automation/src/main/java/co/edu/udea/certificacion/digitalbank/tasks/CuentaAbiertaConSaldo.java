package co.edu.udea.certificacion.digitalbank.tasks;

import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.Tasks;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.equalTo;

public class CuentaAbiertaConSaldo implements Task {

    private final Actor administrador;
    private final long saldo;

    public CuentaAbiertaConSaldo(
            Actor administrador,
            long saldo
    ) {
        this.administrador = administrador;
        this.saldo = saldo;
    }

    public static CuentaAbiertaConSaldo de(
            long saldo,
            Actor administrador
    ) {
        return Tasks.instrumented(
                CuentaAbiertaConSaldo.class,
                administrador,
                saldo
        );
    }

    @Override
    @Step("{0} abre una cuenta de ahorros y la deja con saldo de #saldo pesos")
    public <T extends Actor> void performAs(T actor) {

        actor.attemptsTo(
                AbrirCuenta.deAhorros()
        );

        actor.should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        equalTo(201)
                )
        );

        String numeroCuenta =
                actor.recall("ultimaCuenta");

        actor.remember(
                "cuentaAbiertaConSaldo",
                numeroCuenta
        );

        if (saldo > 0) {
            administrador.attemptsTo(
                    RealizarConsignacion.enLaCuenta(
                            numeroCuenta,
                            saldo
                    )
            );

            administrador.should(
                    seeThat(
                            CodigoDeRespuesta.delServidor(),
                            equalTo(201)
                    )
            );
        }
    }
}