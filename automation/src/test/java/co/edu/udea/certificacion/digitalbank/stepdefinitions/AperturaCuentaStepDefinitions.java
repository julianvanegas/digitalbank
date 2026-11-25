package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import co.edu.udea.certificacion.digitalbank.models.DatosCliente;
import co.edu.udea.certificacion.digitalbank.questions.CodigoDeRespuesta;
import co.edu.udea.certificacion.digitalbank.questions.LlaveDeLaCuenta;
import co.edu.udea.certificacion.digitalbank.questions.MensajeDeError;
import co.edu.udea.certificacion.digitalbank.questions.NumeroDeCuenta;
import co.edu.udea.certificacion.digitalbank.tasks.AbrirCuenta;
import co.edu.udea.certificacion.digitalbank.tasks.AutenticarseConDobleFactor;
import co.edu.udea.certificacion.digitalbank.tasks.CambiarEstadoDelCliente;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import net.serenitybdd.screenplay.Actor;

public class AperturaCuentaStepDefinitions {

    @Dado("que el cliente tiene menos de 3 cuentas no cerradas")
    public void clienteConMenosDeTresCuentas() {
        autenticarCliente();
    }

    @Dado("que el cliente ya tiene 3 cuentas no cerradas")
    public void clienteConTresCuentas() {
        Actor cliente = Actores.cliente();
        autenticarCliente();

        cliente.attemptsTo(
                AbrirCuenta.deAhorros(),
                AbrirCuenta.deAhorros(),
                AbrirCuenta.deAhorros()
        );
    }

    @Dado("que existe un cliente que no está activo")
    public void clienteNoActivo() {
        Actor cliente = Actores.cliente();

        // Primero se crea un cliente activo.
        new ComunesStepDefinitions().clienteActivo();

        // Luego se autentica para conservar una sesión válida.
        autenticarCliente();

        // Finalmente, el administrador cambia su estado a BLOCKED.
        DatosCliente datos = cliente.recall("cliente");

        Actores.administrador().attemptsTo(
                CambiarEstadoDelCliente.a("BLOCKED", datos)
        );

        Actores.administrador().should(
                seeThat(CodigoDeRespuesta.delServidor(), equalTo(204))
        );
    }

    @Cuando("solicita abrir una nueva cuenta de ahorros")
    public void solicitaAbrirCuenta() {
        autenticarClienteSiEsNecesario();

        Actores.cliente().attemptsTo(
                AbrirCuenta.deAhorros()
        );
    }

    @Cuando("solicita abrir una cuenta adicional")
    public void solicitaCuentaAdicional() {
        Actores.cliente().attemptsTo(
                AbrirCuenta.deAhorros()
        );
    }

    @Cuando("abre dos cuentas de ahorros para validar su unicidad")
    public void abreDosCuentas() {
        Actor cliente = Actores.cliente();

        autenticarClienteSiEsNecesario();

        // Primera cuenta
        cliente.attemptsTo(
                AbrirCuenta.deAhorros()
        );

        cliente.remember(
                "primeraCuenta",
                cliente.asksFor(NumeroDeCuenta.generado())
        );

        cliente.remember(
                "primeraLlave",
                cliente.asksFor(LlaveDeLaCuenta.generada())
        );

        // Segunda cuenta
        cliente.attemptsTo(
                AbrirCuenta.deAhorros()
        );

        cliente.remember(
                "segundaCuenta",
                cliente.asksFor(NumeroDeCuenta.generado())
        );

        cliente.remember(
                "segundaLlave",
                cliente.asksFor(LlaveDeLaCuenta.generada())
        );
    }

    @Entonces("la cuenta es creada correctamente")
    public void cuentaCreadaCorrectamente() {
        Actores.cliente().should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        equalTo(201)
                )
        );
    }

    @Entonces("el número de cuenta tiene 11 dígitos")
    public void numeroTieneOnceDigitos() {
        String numero = Actores.cliente().asksFor(
                NumeroDeCuenta.generado()
        );

        if (numero == null || !numero.matches("\\d{11}")) {
            throw new AssertionError(
                    "El número de cuenta debería tener exactamente 11 dígitos numéricos, pero fue: " + numero
            );
        }
    }

    @Entonces("la cuenta tiene una llave única")
    public void cuentaTieneLlaveUnica() {
        String llave = Actores.cliente().asksFor(
                LlaveDeLaCuenta.generada()
        );

        if (llave == null || llave.isBlank()) {
            throw new AssertionError(
                    "La cuenta no recibió una llave válida"
            );
        }
    }

    @Entonces("recibe el rechazo de la apertura")
    public void recibeRechazoApertura() {
        Actores.cliente().should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        not(equalTo(201))
                )
        );
    }

    @Entonces("ve un mensaje que indica que alcanzó el máximo de cuentas")
    public void mensajeMaximoCuentas() {
        Actores.cliente().should(
                seeThat(
                        MensajeDeError.delServidor(),
                        containsString("máximo")
                )
        );
    }

    @Entonces("el número de cuenta tiene exactamente 11 dígitos numéricos")
    public void numeroExactamenteOnceDigitos() {
        numeroTieneOnceDigitos();
    }

    @Entonces("los números de cuenta son diferentes")
    public void numerosSonDiferentes() {
        Actor cliente = Actores.cliente();

        String primera = cliente.recall("primeraCuenta");
        String segunda = cliente.recall("segundaCuenta");

        if (primera == null || segunda == null || primera.equals(segunda)) {
            throw new AssertionError(
                    "Los números de cuenta deberían ser únicos"
            );
        }
    }

    @Entonces("las llaves de las cuentas son diferentes")
    public void llavesSonDiferentes() {
        Actor cliente = Actores.cliente();

        String primera = cliente.recall("primeraLlave");
        String segunda = cliente.recall("segundaLlave");

        if (primera == null || segunda == null || primera.equals(segunda)) {
            throw new AssertionError(
                    "Las llaves de las cuentas deberían ser únicas"
            );
        }
    }

    @Entonces("no obtiene una cuenta nueva")
    public void noObtieneCuentaNueva() {
        Actores.cliente().should(
                seeThat(
                        CodigoDeRespuesta.delServidor(),
                        not(equalTo(201))
                )
        );
    }

    private void autenticarCliente() {
        Actor cliente = Actores.cliente();
        DatosCliente datos = cliente.recall("cliente");

        cliente.attemptsTo(
                AutenticarseConDobleFactor.con(
                        datos.correo(),
                        DatosCliente.CONTRASENA_VALIDA
                )
        );
    }

    private void autenticarClienteSiEsNecesario() {
        Actor cliente = Actores.cliente();

        String token = cliente.recall("token");

        if (token == null || token.isBlank()) {
            autenticarCliente();
        }
    }
}