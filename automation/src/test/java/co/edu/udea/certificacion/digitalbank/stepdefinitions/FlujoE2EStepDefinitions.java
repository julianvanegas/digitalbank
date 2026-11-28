package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Entonces;
import net.serenitybdd.screenplay.Actor;

/**
 * Pasos propios del flujo E2E (@E2E). El resto del flujo reutiliza los pasos de HU01, HU02, HU04, HU08, HU12, HU18,
 * HU19 y HU20 (el cierre de sesión y el rechazo del token anterior son los de HU04).
 */
public class FlujoE2EStepDefinitions {

    @Cuando("abre dos cuentas de ahorros")
    public void abreDosCuentas() {
        Actor cliente = Actores.cliente();
        Preparacion.cuentaConSaldo(cliente, TransaccionesStepDefinitions.CUENTA_ORIGEN, 0);
        Preparacion.cuentaConSaldo(cliente, TransaccionesStepDefinitions.CUENTA_DESTINO, 0);
    }

    @Entonces("para volver a entrar debe autenticarse de nuevo")
    public void debeAutenticarseDeNuevo() {
        Actor cliente = Actores.cliente();
        Preparacion.iniciarSesion(cliente);
        Preparacion.verificarQueSuSesionEsValida(cliente);
    }
}
