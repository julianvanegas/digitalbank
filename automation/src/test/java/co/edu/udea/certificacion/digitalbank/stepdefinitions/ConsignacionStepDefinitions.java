package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyOrNullString;

import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.MensajeDeError;
import co.edu.udea.certificacion.digitalbank.questions.ReferenciaDeConsignacion;
import co.edu.udea.certificacion.digitalbank.questions.SaldoDeLaCuenta;
import co.edu.udea.certificacion.digitalbank.tasks.AbrirCuenta;
import co.edu.udea.certificacion.digitalbank.tasks.AutenticarseConDobleFactor;
import co.edu.udea.certificacion.digitalbank.tasks.BloquearCuenta;
import co.edu.udea.certificacion.digitalbank.tasks.PrepararClienteActivo;
import co.edu.udea.certificacion.digitalbank.tasks.RealizarConsignacion;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import java.math.BigDecimal;
import net.serenitybdd.screenplay.Actor;

public class ConsignacionStepDefinitions {

    private static final String CUENTA = "cuentaConsignacion";
    private static final String CUENTA_INEXISTENTE = "999999999999";

    @Dado("que existe un cliente activo con una cuenta activa")
    public void existeClienteActivoConCuentaActiva() {
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

        String numeroCuenta = cliente.recall("ultimaCuenta");

        cliente.remember(
                CUENTA,
                numeroCuenta
        );
    }

    @Dado("la cuenta tiene saldo inicial de {long} pesos")
    public void cuentaTieneSaldoInicial(long saldoEsperado) {
        Actor cliente = Actores.cliente();
        String cuenta = cliente.recall(CUENTA);

        cliente.should(
                seeThat(
                        SaldoDeLaCuenta.numero(cuenta),
                        comparesEqualTo(BigDecimal.valueOf(saldoEsperado))
                )
        );
    }

    @Dado("que se tiene un número de cuenta que no existe")
    public void numeroCuentaNoExiste() {
        Actores.administrador().remember(
                "cuentaInexistente",
                CUENTA_INEXISTENTE
        );
    }

    @Dado("que existe un cliente activo con una cuenta bloqueada")
    public void existeClienteActivoConCuentaBloqueada() {
        existeClienteActivoConCuentaActiva();

        String cuenta =
                Actores.cliente().recall(CUENTA);

        Actores.administrador().attemptsTo(
                BloquearCuenta.numero(cuenta)
        );

        Actores.administrador().should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        equalTo(200)
                )
        );
    }

    @Dado("el saldo actual de la cuenta es guardado")
    public void guardarSaldoActual() {
        Actor cliente = Actores.cliente();

        String cuenta =
                cliente.recall(CUENTA);

        BigDecimal saldo =
                cliente.asksFor(
                        SaldoDeLaCuenta.numero(cuenta)
                );

        cliente.remember(
                "saldoAntesConsignacion",
                saldo
        );
    }

    @Cuando("el administrador consigna {long} pesos en la cuenta")
    public void administradorConsigna(long monto) {
        String cuenta =
                Actores.cliente().recall(CUENTA);

        Actores.administrador().attemptsTo(
                RealizarConsignacion.enLaCuenta(
                        cuenta,
                        monto
                )
        );
    }

    @Cuando("el administrador intenta consignar {long} pesos en esa cuenta")
    public void administradorConsignaEnCuentaInexistente(long monto) {
        String cuenta =
                Actores.administrador().recall(
                        "cuentaInexistente"
                );

        Actores.administrador().attemptsTo(
                RealizarConsignacion.enLaCuenta(
                        cuenta,
                        monto
                )
        );
    }

    @Cuando("el administrador intenta consignar {long} pesos en la cuenta bloqueada")
    public void administradorConsignaEnCuentaBloqueada(long monto) {
        String cuenta =
                Actores.cliente().recall(CUENTA);

        Actores.administrador().attemptsTo(
                RealizarConsignacion.enLaCuenta(
                        cuenta,
                        monto
                )
        );
    }

    @Cuando("el cliente intenta consignar {long} pesos en su cuenta")
    public void clienteIntentaConsignar(long monto) {
        Actor cliente = Actores.cliente();

        String cuenta =
                cliente.recall(CUENTA);

        cliente.attemptsTo(
                RealizarConsignacion.enLaCuenta(
                        cuenta,
                        monto
                )
        );
    }

    @Cuando("el administrador realiza dos consignaciones de {long} pesos")
    public void administradorRealizaDosConsignaciones(long monto) {
        Actor administrador =
                Actores.administrador();

        String cuenta =
                Actores.cliente().recall(CUENTA);

        administrador.attemptsTo(
                RealizarConsignacion.enLaCuenta(
                        cuenta,
                        monto
                )
        );

        administrador.should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        equalTo(201)
                )
        );

        administrador.remember(
                "primeraReferenciaConsignacion",
                administrador.asksFor(
                        ReferenciaDeConsignacion.generada()
                )
        );

        administrador.attemptsTo(
                RealizarConsignacion.enLaCuenta(
                        cuenta,
                        monto
                )
        );

        administrador.should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        equalTo(201)
                )
        );

        administrador.remember(
                "segundaReferenciaConsignacion",
                administrador.asksFor(
                        ReferenciaDeConsignacion.generada()
                )
        );
    }

    @Entonces("la consignación es registrada correctamente")
    public void consignacionRegistradaCorrectamente() {
        Actores.administrador().should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        equalTo(201)
                )
        );
    }

    @Entonces("recibe un comprobante de consignación")
    public void recibeComprobanteConsignacion() {
        Actores.administrador().should(
                seeThat(
                        ReferenciaDeConsignacion.generada(),
                        not(isEmptyOrNullString())
                )
        );
    }

    @Entonces("el saldo de la cuenta queda en {long} pesos")
    public void saldoCuentaQuedaEn(long saldoEsperado) {
        Actor cliente =
                Actores.cliente();

        String cuenta =
                cliente.recall(CUENTA);

        cliente.should(
                seeThat(
                        SaldoDeLaCuenta.numero(cuenta),
                        comparesEqualTo(
                                BigDecimal.valueOf(
                                        saldoEsperado
                                )
                        )
                )
        );
    }

    @Entonces("recibe el rechazo de la consignación")
    public void recibeRechazoConsignacion() {
        Actores.administrador().should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        not(equalTo(201))
                )
        );
    }

    @Entonces("ve un mensaje que indica que la cuenta no existe")
    public void mensajeCuentaNoExiste() {
        Actores.administrador().should(
            seeThat(
                    MensajeDeError.delServidor(),
                    containsString("cuenta")
            )
    );
}


    @Entonces("ve un mensaje que indica que la cuenta no permite créditos")
    public void mensajeCuentaNoPermiteCreditos() {
        Actores.administrador().should(
            seeThat(
                    MensajeDeError.delServidor(),
                    containsString("cuenta")
            )
    );
}

    @Entonces("recibe el rechazo de la consignación por falta de permisos")
    public void rechazoPorFaltaDePermisos() {
        Actores.cliente().should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        equalTo(403)
                )
        );
    }

    @Entonces("el saldo de la cuenta no cambia")
    public void saldoCuentaNoCambia() {
        Actor cliente =
                Actores.cliente();

        String cuenta =
                cliente.recall(CUENTA);

        BigDecimal saldoAnterior =
                cliente.recall(
                        "saldoAntesConsignacion"
                );

        cliente.should(
                seeThat(
                        SaldoDeLaCuenta.numero(cuenta),
                        comparesEqualTo(
                                saldoAnterior
                        )
                )
        );
    }

    @Entonces("los comprobantes de las consignaciones son diferentes")
    public void comprobantesSonDiferentes() {
        Actor administrador =
                Actores.administrador();

        String primera =
                administrador.recall(
                        "primeraReferenciaConsignacion"
                );

        String segunda =
                administrador.recall(
                        "segundaReferenciaConsignacion"
                );

        if (primera == null
                || segunda == null
                || primera.equals(segunda)) {

            throw new AssertionError(
                    "Los comprobantes de las consignaciones deberían ser únicos. "
                            + "Primera referencia: "
                            + primera
                            + ", segunda referencia: "
                            + segunda
            );
        }
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