package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static co.edu.udea.certificacion.digitalbank.matchers.CoincideCon.elPatron;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import co.edu.udea.certificacion.digitalbank.questions.CampoDeLaRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.CantidadDeCuentas;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.LlaveDeLaCuenta;
import co.edu.udea.certificacion.digitalbank.questions.NumeroDeCuenta;
import co.edu.udea.certificacion.digitalbank.questions.SaldoDeLaCuenta;
import co.edu.udea.certificacion.digitalbank.tasks.AbrirCuenta;
import co.edu.udea.certificacion.digitalbank.tasks.CambiarEstadoDelCliente;
import co.edu.udea.certificacion.digitalbank.tasks.ConsultarCuentasDelCliente;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import java.math.BigDecimal;
import java.util.Locale;
import net.serenitybdd.screenplay.Actor;

/** HU10: apertura de cuentas de ahorros. Autor original: integrante C. */
public class AperturaCuentaStepDefinitions {

    /** HU10: el número de cuenta tiene exactamente 11 dígitos numéricos. */
    private static final String FORMATO_NUMERO_CUENTA = "\\d{11}";

    @Dado("que el cliente ya tiene 3 cuentas no cerradas")
    public void clienteConTresCuentas() {
        Actor cliente = Actores.cliente();
        for (int cuenta = 1; cuenta <= 3; cuenta++) {
            Preparacion.cuentaConSaldo(cliente, "cuenta" + cuenta, 0);
        }
    }

    @Dado("que existe un cliente que no está activo")
    public void clienteNoActivo() {
        Actor cliente = Actores.cliente();
        Preparacion.clienteActivo(cliente);
        Preparacion.iniciarSesion(cliente);
        DatosCliente datos = cliente.recall("cliente");
        Actores.administrador().attemptsTo(CambiarEstadoDelCliente.a("BLOCKED", datos));
        Actores.administrador().should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(204)));
    }

    @Cuando("solicita abrir una nueva cuenta de ahorros")
    public void solicitaAbrirCuenta() {
        Actores.cliente().attemptsTo(AbrirCuenta.deAhorros());
    }

    @Cuando("abre dos cuentas de ahorros para validar su unicidad")
    public void abreDosCuentas() {
        Actor cliente = Actores.cliente();
        cliente.attemptsTo(AbrirCuenta.deAhorros());
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(201)));
        cliente.remember("primeraCuenta", cliente.asksFor(NumeroDeCuenta.generado()));
        cliente.remember("primeraLlave", cliente.asksFor(LlaveDeLaCuenta.generada()));
        cliente.attemptsTo(AbrirCuenta.deAhorros());
        cliente.should(seeThat(CodigoDeRespuesta.delServidor(), equalTo(201)));
    }

    @Entonces("la cuenta es creada activa, de ahorros y con saldo cero")
    public void cuentaCreadaCorrectamente() {
        Actores.cliente().should(
                seeThat(CodigoDeRespuesta.delServidor(), equalTo(201)),
                seeThat(CampoDeLaRespuesta.llamado("status"), equalTo("ACTIVE")),
                seeThat(CampoDeLaRespuesta.llamado("accountType"), equalTo("SAVINGS")),
                seeThat(SaldoDeLaCuenta.enLaRespuesta(), comparesEqualTo(BigDecimal.ZERO)));
    }

    @Entonces("el número de cuenta tiene exactamente 11 dígitos numéricos")
    public void numeroConOnceDigitos() {
        Actores.cliente().should(seeThat(NumeroDeCuenta.generado(), elPatron(FORMATO_NUMERO_CUENTA)));
    }

    /**
     * HU10: "@" + los dos primeros caracteres del primer nombre + los tres últimos dígitos del documento + el
     * indicativo de la cuenta (1 para la primera), con las letras en minúscula.
     */
    @Entonces("la llave de la primera cuenta sigue el formato de la historia")
    public void llaveConFormatoDeLaHistoria() {
        DatosCliente datos = Actores.cliente().recall("cliente");
        String documento = datos.numeroDocumento();
        String esperada = "@" + datos.nombres().substring(0, 2).toLowerCase(Locale.ROOT)
                + documento.substring(documento.length() - 3) + "1";
        Actores.cliente().should(seeThat(LlaveDeLaCuenta.generada(), equalTo(esperada)));
    }

    @Entonces("los números de cuenta son diferentes")
    public void numerosSonDiferentes() {
        String primera = Actores.cliente().recall("primeraCuenta");
        Actores.cliente().should(seeThat(NumeroDeCuenta.generado(), not(equalTo(primera))));
    }

    @Entonces("las llaves de las cuentas son diferentes")
    public void llavesSonDiferentes() {
        String primera = Actores.cliente().recall("primeraLlave");
        Actores.cliente().should(seeThat(LlaveDeLaCuenta.generada(), not(equalTo(primera))));
    }

    @Entonces("el cliente no tiene cuentas")
    public void clienteSinCuentas() {
        DatosCliente datos = Actores.cliente().recall("cliente");
        Actor administrador = Actores.administrador();
        administrador.attemptsTo(ConsultarCuentasDelCliente.conDocumento(datos));
        administrador.should(
                seeThat(CodigoDeRespuesta.delServidor(), equalTo(200)),
                seeThat(CantidadDeCuentas.enLaRespuesta(), equalTo(0)));
    }
}
