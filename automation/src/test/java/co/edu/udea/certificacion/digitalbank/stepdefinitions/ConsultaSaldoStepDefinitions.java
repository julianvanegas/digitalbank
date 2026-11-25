package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.SaldoDeLaCuenta;
import co.edu.udea.certificacion.digitalbank.tasks.AbrirCuenta;
import co.edu.udea.certificacion.digitalbank.tasks.AutenticarseConDobleFactor;
import co.edu.udea.certificacion.digitalbank.tasks.PrepararClienteActivo;
import co.edu.udea.certificacion.digitalbank.tasks.RealizarConsignacion;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import java.math.BigDecimal;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Actor;

public class ConsultaSaldoStepDefinitions {

    private static final String CUENTA_PROPIA = "cuentaSaldoPropia";
    private static final String CUENTA_OTRO_CLIENTE = "cuentaSaldoOtroCliente";

    @Dado("que existe un cliente activo con una cuenta activa y saldo de {long} pesos")
    public void clienteActivoConCuentaYSaldo(long saldo) {
        Actor cliente = Actores.cliente();

        cliente.attemptsTo(
                PrepararClienteActivo.aprobadoPor(
                        Actores.administrador()
                )
        );

        Actores.administrador().should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        equalTo(204)
                )
        );

        autenticarCliente(cliente);

        cliente.attemptsTo(
                AbrirCuenta.deAhorros()
        );

        cliente.should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        equalTo(201)
                )
        );

        String cuenta = cliente.recall("ultimaCuenta");

        cliente.remember(
                CUENTA_PROPIA,
                cuenta
        );

        if (saldo > 0) {
            Actores.administrador().attemptsTo(
                    RealizarConsignacion.enLaCuenta(
                            cuenta,
                            saldo
                    )
            );

            Actores.administrador().should(
                    seeThat(
                            CodigoDeRespuesta.delServidor(),
                            equalTo(201)
                    )
            );
        }
    }

    @Dado("que existe un cliente activo autenticado")
    public void clienteActivoAutenticado() {
        Actor cliente = Actores.cliente();

        cliente.attemptsTo(
                PrepararClienteActivo.aprobadoPor(
                        Actores.administrador()
                )
        );

        Actores.administrador().should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        equalTo(204)
                )
        );

        autenticarCliente(cliente);
    }

    @Dado("que otro cliente tiene una cuenta activa con saldo de {long} pesos")
    public void otroClienteConCuentaYSaldo(long saldo) {
        Actor otroCliente = Actores.clienteExistente();

        otroCliente.attemptsTo(
                PrepararClienteActivo.aprobadoPor(
                        Actores.administrador()
                )
        );

        Actores.administrador().should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        equalTo(204)
                )
        );

        autenticarCliente(otroCliente);

        otroCliente.attemptsTo(
                AbrirCuenta.deAhorros()
        );

        otroCliente.should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        equalTo(201)
                )
        );

        String cuentaOtroCliente =
                otroCliente.recall("ultimaCuenta");

        Actores.cliente().remember(
                CUENTA_OTRO_CLIENTE,
                cuentaOtroCliente
        );

        if (saldo > 0) {
            Actores.administrador().attemptsTo(
                    RealizarConsignacion.enLaCuenta(
                            cuentaOtroCliente,
                            saldo
                    )
            );

            Actores.administrador().should(
                    seeThat(
                            CodigoDeRespuesta.delServidor(),
                            equalTo(201)
                    )
            );
        }
    }

    @Dado("guarda el saldo actual de su cuenta")
    public void guardaSaldoActual() {
        Actor cliente = Actores.cliente();

        String cuenta =
                cliente.recall(CUENTA_PROPIA);

        BigDecimal saldo =
                cliente.asksFor(
                        SaldoDeLaCuenta.numero(cuenta)
                );

        cliente.remember(
                "saldoAntesConsulta",
                saldo
        );
    }

    @Cuando("el cliente consulta el saldo de su cuenta")
    public void clienteConsultaSaldo() {
        Actor cliente = Actores.cliente();

        String cuenta =
                cliente.recall(CUENTA_PROPIA);

        BigDecimal saldo =
                cliente.asksFor(
                        SaldoDeLaCuenta.numero(cuenta)
                );

        cliente.remember(
                "ultimoSaldoConsultado",
                saldo
        );
    }

    @Cuando("el cliente intenta consultar el saldo de la cuenta del otro cliente")
    public void clienteConsultaSaldoAjeno() {
        Actor cliente = Actores.cliente();

        String cuenta =
                cliente.recall(CUENTA_OTRO_CLIENTE);

        cliente.attemptsTo(
                new net.serenitybdd.screenplay.Performable() {
                    @Override
                    public <T extends Actor> void performAs(T actor) {
                        SaldoDeLaCuenta.numero(cuenta).answeredBy(actor);
                    }
                }
        );
    }

    @Entonces("ve un saldo de {long} pesos")
    public void veSaldo(long saldoEsperado) {
        Actor cliente = Actores.cliente();

        BigDecimal saldo =
                cliente.recall(
                        "ultimoSaldoConsultado"
                );

        if (saldo == null) {
            String cuenta =
                    cliente.recall(CUENTA_PROPIA);

            saldo =
                    cliente.asksFor(
                            SaldoDeLaCuenta.numero(cuenta)
                    );
        }

        if (saldo.compareTo(
                BigDecimal.valueOf(
                        saldoEsperado
                )
        ) != 0) {
            throw new AssertionError(
                    "El saldo esperado era "
                            + saldoEsperado
                            + " pero se obtuvo "
                            + saldo
            );
        }
    }

    @Entonces("recibe el rechazo de la consulta de saldo")
    public void rechazoConsultaSaldo() {
        Actores.cliente().should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        not(equalTo(200))
                )
        );
    }

    @Entonces("no ve información financiera de la cuenta")
    public void noVeInformacionFinanciera() {
        String respuesta =
                SerenityRest.lastResponse()
                        .getBody()
                        .asString();

        if (respuesta.contains("\"balance\"")) {
            throw new AssertionError(
                    "La respuesta no debería revelar el saldo de una cuenta perteneciente a otro cliente"
            );
        }
    }

    @Entonces("el saldo de la cuenta permanece sin modificaciones")
    public void saldoPermaneceSinCambios() {
        Actor cliente = Actores.cliente();

        String cuenta =
                cliente.recall(CUENTA_PROPIA);

        BigDecimal saldoAntes =
                cliente.recall(
                        "saldoAntesConsulta"
                );

        cliente.should(
                seeThat(
                        SaldoDeLaCuenta.numero(cuenta),
                        comparesEqualTo(
                                saldoAntes
                        )
                )
        );
    }

    private void autenticarCliente(Actor cliente) {
        DatosCliente datos =
                cliente.recall("cliente");

        cliente.attemptsTo(
                AutenticarseConDobleFactor.con(
                        datos.correo(),
                        DatosCliente.CONTRASENA_VALIDA
                )
        );

        cliente.should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        equalTo(200)
                )
        );
    }
}