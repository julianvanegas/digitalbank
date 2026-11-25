package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.equalTo;

import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.tasks.ConsultarMiPerfil;
import co.edu.udea.certificacion.digitalbank.tasks.provisional.CerrarSesion;
import co.edu.udea.certificacion.digitalbank.tasks.provisional.RealizarConsignacion;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Entonces;
import net.serenitybdd.screenplay.Actor;

/**
 * Pasos propios del flujo E2E (@E2E). El resto del flujo reutiliza los pasos de HU01, HU02, HU08, HU19 y HU20.
 */
public class FlujoE2EStepDefinitions {

    @Cuando("abre dos cuentas de ahorros")
    public void abreDosCuentas() {
        Actor cliente = Actores.cliente();
        TransaccionesStepDefinitions.abrirCuentaConSaldo(cliente, TransaccionesStepDefinitions.CUENTA_ORIGEN, 0);
        TransaccionesStepDefinitions.abrirCuentaConSaldo(cliente, TransaccionesStepDefinitions.CUENTA_DESTINO, 0);
    }

    @Cuando("el administrador consigna {long} pesos en la cuenta de origen")
    public void administradorConsigna(long monto) {
        Actor administrador = Actores.administrador();
        String cuenta = Actores.cliente().recall(TransaccionesStepDefinitions.CUENTA_ORIGEN);
        administrador.attemptsTo(RealizarConsignacion.enLaCuenta(cuenta, monto));
        administrador.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(201)));
    }

    @Cuando("cierra la sesión")
    public void cierraLaSesion() {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(CerrarSesion.ahora());
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(204)));
    }

    @Entonces("su token anterior es rechazado")
    public void tokenAnteriorRechazado() {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(ConsultarMiPerfil.conToken(cliente.recall("tokenAnterior")));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(401)));
    }

    @Entonces("para volver a entrar debe autenticarse de nuevo")
    public void debeAutenticarseDeNuevo() {
        Actor cliente = Actores.cliente();
        TransaccionesStepDefinitions.iniciarSesion(cliente);
        cliente.attemptsTo(ConsultarMiPerfil.conToken(cliente.recall("token")));
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)));
    }
}
